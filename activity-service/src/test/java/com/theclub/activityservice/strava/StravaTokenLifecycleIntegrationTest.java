package com.theclub.activityservice.strava;

import com.theclub.activityservice.client.NotificationClient;
import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.model.StravaConnectionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StravaTokenLifecycleIntegrationTest extends StravaIntegrationTestBase {

    @Autowired
    StravaTokenManager tokens;
    @Autowired
    StravaTokenMigration migration;

    private StravaTokenResponse rotated(String access, String refresh) {
        return new StravaTokenResponse(access, refresh, clock.instant().plus(Duration.ofHours(6)).getEpochSecond(), null);
    }

    @Test
    void expiringTokenIsRefreshedAndTheRotatedPairIsPersistedEncrypted() {
        StravaConnection connection = connect(UUID.randomUUID(), 1L, Duration.ofSeconds(30));
        when(stravaClient.refresh("refresh-0")).thenReturn(rotated("access-1", "refresh-1"));

        assertThat(tokens.accessToken(connection)).isEqualTo("access-1");

        StravaConnection stored = connections.findById(connection.getId()).orElseThrow();
        assertThat(stored.getAccessToken()).startsWith("v1:").doesNotContain("access-1");
        assertThat(stored.getRefreshToken()).startsWith("v1:");
        assertThat(cipher.decrypt(stored.getRefreshToken())).isEqualTo("refresh-1");
        assertThat(stored.getExpiresAt()).isAfter(clock.instant().plus(Duration.ofHours(5)));

        // A caller still holding the stale entity does not refresh again: the fresh stored token is reused
        assertThat(tokens.accessToken(connection)).isEqualTo("access-1");
        verify(stravaClient, times(1)).refresh(anyString());
    }

    @Test
    void unauthorizedCallIsRetriedOnceAfterAForcedRefresh() {
        StravaConnection connection = connect(UUID.randomUUID(), 2L, Duration.ofHours(5));
        when(stravaClient.refresh("refresh-0")).thenReturn(rotated("access-1", "refresh-1"));
        AtomicInteger calls = new AtomicInteger();

        String result = tokens.withAccessToken(connection, token -> {
            calls.incrementAndGet();
            if (token.equals("access-0")) {
                throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null);
            }
            return "ok with " + token;
        });

        assertThat(result).isEqualTo("ok with access-1");
        assertThat(calls).hasValue(2);
    }

    @Test
    void rejectedRefreshTokenRevokesTheConnectionAndNotifiesTheMember() {
        UUID userId = UUID.randomUUID();
        StravaConnection connection = connect(userId, 3L, Duration.ofSeconds(10));
        when(stravaClient.refresh("refresh-0")).thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,
                "Bad Request", null, "{\"message\":\"Bad Request\",\"errors\":[{\"code\":\"invalid\"}]}".getBytes(), null));

        assertThatThrownBy(() -> tokens.accessToken(connection)).isInstanceOf(StravaAccessRevokedException.class);

        StravaConnection stored = connections.findById(connection.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(StravaConnectionStatus.REVOKED);
        assertThat(stored.getAccessToken()).isNull();
        assertThat(stored.getRefreshToken()).isNull();
        assertThat(stored.getRevokedAt()).isEqualTo(clock.instant());
        ArgumentCaptor<NotificationClient.NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationClient.NotificationRequest.class);
        verify(notificationClient).create(sent.capture());
        assertThat(sent.getValue().userId()).isEqualTo(userId);
        assertThat(sent.getValue().type()).isEqualTo("STRAVA_DISCONNECTED");

        // Revoked connections fail fast without calling Strava
        assertThatThrownBy(() -> tokens.accessToken(stored)).isInstanceOf(StravaAccessRevokedException.class);
        verify(stravaClient, times(1)).refresh(anyString());
    }

    @Test
    void unauthorizedEvenWithAFreshTokenMeansRevoked() {
        StravaConnection connection = connect(UUID.randomUUID(), 4L, Duration.ofHours(5));
        when(stravaClient.refresh("refresh-0")).thenReturn(rotated("access-1", "refresh-1"));

        assertThatThrownBy(() -> tokens.withAccessToken(connection, token -> {
            throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null);
        })).isInstanceOf(StravaAccessRevokedException.class);

        assertThat(connections.findById(connection.getId()).orElseThrow().getStatus())
                .isEqualTo(StravaConnectionStatus.REVOKED);
        verify(notificationClient).create(any());
    }

    @Test
    void notificationFailureDoesNotBreakRevocation() {
        StravaConnection connection = connect(UUID.randomUUID(), 5L, Duration.ofSeconds(10));
        when(stravaClient.refresh("refresh-0")).thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                "Unauthorized", null, null, null));
        org.mockito.Mockito.doThrow(new RuntimeException("notification-service down"))
                .when(notificationClient).create(any());

        assertThatThrownBy(() -> tokens.accessToken(connection)).isInstanceOf(StravaAccessRevokedException.class);
        assertThat(connections.findById(connection.getId()).orElseThrow().getStatus())
                .isEqualTo(StravaConnectionStatus.REVOKED);
    }

    @Test
    void legacyPlaintextTokensAreEncryptedOnStartup() {
        StravaConnection legacy = new StravaConnection(UUID.randomUUID(), 6L);
        legacy.setAccessToken("plain-access");
        legacy.setRefreshToken("plain-refresh");
        legacy.setExpiresAt(clock.instant().plus(Duration.ofHours(1)));
        connections.save(legacy);

        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments()); // idempotent

        StravaConnection stored = connections.findById(legacy.getId()).orElseThrow();
        assertThat(stored.getAccessToken()).startsWith("v1:");
        assertThat(cipher.decrypt(stored.getAccessToken())).isEqualTo("plain-access");
        assertThat(cipher.decrypt(stored.getRefreshToken())).isEqualTo("plain-refresh");
        verify(stravaClient, never()).refresh(anyString());
    }
}

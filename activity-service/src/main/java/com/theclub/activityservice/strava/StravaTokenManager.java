package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.model.StravaConnectionStatus;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * Supplies valid access tokens and runs calls with them.
 * <ul>
 *   <li>Access tokens live 6 hours; they are refreshed shortly before expiry, and once more on a 401.</li>
 *   <li>Refresh tokens rotate: the refresh happens under a row lock and the new pair is persisted in the same
 *       transaction, so concurrent refreshes (threads or replicas) never lose the latest refresh token.</li>
 *   <li>If Strava rejects the refresh token (400/401, e.g. invalid_grant) or still answers 401 with a fresh token,
 *       access was revoked: the connection is marked REVOKED, tokens are cleared and the member is notified.</li>
 * </ul>
 */
@Component
public class StravaTokenManager {

    static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(StravaTokenManager.class);

    private final StravaClient client;
    private final StravaConnectionRepository connections;
    private final StravaTokenCipher cipher;
    private final StravaNotifier notifier;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public StravaTokenManager(StravaClient client, StravaConnectionRepository connections, StravaTokenCipher cipher,
                              StravaNotifier notifier, PlatformTransactionManager transactionManager, Clock clock) {
        this.client = client;
        this.connections = connections;
        this.cipher = cipher;
        this.notifier = notifier;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Runs {@code call} with a valid access token, refreshing it first if it's about to expire and retrying once
     * after a forced refresh if Strava answers 401.
     *
     * @throws StravaAccessRevokedException when the authorization is no longer valid
     */
    public <T> T withAccessToken(StravaConnection connection, Function<String, T> call) {
        String token = accessToken(connection);
        try {
            return call.apply(token);
        } catch (HttpClientErrorException.Unauthorized e) {
            String refreshed = refresh(connection.getId(), token, true, false);
            try {
                return call.apply(refreshed);
            } catch (HttpClientErrorException.Unauthorized again) {
                revoke(connection.getId(), "Strava rejected a freshly refreshed token");
                throw new StravaAccessRevokedException(connection.getUserId());
            }
        }
    }

    /** A usable access token (plaintext), refreshed when it expires within {@link #REFRESH_MARGIN}. */
    public String accessToken(StravaConnection connection) {
        if (connection.getStatus() != StravaConnectionStatus.ACTIVE || connection.getAccessToken() == null) {
            throw new StravaAccessRevokedException(connection.getUserId());
        }
        String current = cipher.decrypt(connection.getAccessToken());
        if (connection.getExpiresAt().isAfter(clock.instant().plus(REFRESH_MARGIN))) {
            return current;
        }
        return refresh(connection.getId(), current, false, false);
    }

    /**
     * Always asks Strava for a new token pair (persisting the rotation). Used to confirm an unauthenticated
     * "deauthorized" webhook: a revoked app can no longer refresh.
     *
     * @throws StravaAccessRevokedException when Strava rejects the refresh token
     */
    public void verifyAuthorization(UUID connectionId) {
        refresh(connectionId, null, true, true);
    }

    /**
     * Refreshes under a row lock. If another thread/replica already refreshed (the stored token differs from the
     * one the caller saw and is still valid), that token is reused instead of refreshing again.
     */
    String refresh(UUID connectionId, String tokenSeenByCaller, boolean force, boolean always) {
        RefreshOutcome outcome = transactions.execute(status -> {
            StravaConnection locked = connections.findByIdForUpdate(connectionId)
                    .orElseThrow(() -> new IllegalStateException("Strava connection disappeared"));
            if (locked.getStatus() != StravaConnectionStatus.ACTIVE || locked.getRefreshToken() == null) {
                return RefreshOutcome.revoked(locked.getUserId());
            }
            String stored = cipher.decrypt(locked.getAccessToken());
            boolean storedStillValid = locked.getExpiresAt().isAfter(clock.instant().plus(REFRESH_MARGIN));
            if (!always && storedStillValid && (!force || !stored.equals(tokenSeenByCaller))) {
                return RefreshOutcome.token(stored);
            }
            StravaTokenResponse refreshed;
            try {
                refreshed = client.refresh(cipher.decrypt(locked.getRefreshToken()));
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401) {
                    markRevoked(locked, "refresh token rejected (" + e.getStatusCode().value() + ")");
                    return RefreshOutcome.revoked(locked.getUserId());
                }
                throw e;
            }
            if (refreshed == null || refreshed.accessToken() == null || refreshed.refreshToken() == null) {
                throw new RestClientException("Strava returned an empty token response");
            }
            locked.setAccessToken(cipher.encrypt(refreshed.accessToken()));
            locked.setRefreshToken(cipher.encrypt(refreshed.refreshToken()));
            locked.setExpiresAt(Instant.ofEpochSecond(refreshed.expiresAt()));
            connections.save(locked);
            return RefreshOutcome.token(refreshed.accessToken());
        });
        if (outcome.revokedUser() != null) {
            throw new StravaAccessRevokedException(outcome.revokedUser());
        }
        return outcome.accessToken();
    }

    /** Marks the connection REVOKED, clears its tokens and notifies the member. */
    public void revoke(UUID connectionId, String reason) {
        transactions.executeWithoutResult(status -> connections.findByIdForUpdate(connectionId)
                .filter(connection -> connection.getStatus() == StravaConnectionStatus.ACTIVE)
                .ifPresent(connection -> markRevoked(connection, reason)));
    }

    private void markRevoked(StravaConnection connection, String reason) {
        connection.setStatus(StravaConnectionStatus.REVOKED);
        connection.setAccessToken(null);
        connection.setRefreshToken(null);
        connection.setRevokedAt(clock.instant());
        connections.save(connection);
        log.info("Strava access for user {} revoked: {}", connection.getUserId(), reason);
        notifier.disconnected(connection.getUserId());
    }

    private record RefreshOutcome(String accessToken, UUID revokedUser) {
        static RefreshOutcome token(String token) {
            return new RefreshOutcome(token, null);
        }

        static RefreshOutcome revoked(UUID userId) {
            return new RefreshOutcome(null, userId);
        }
    }
}

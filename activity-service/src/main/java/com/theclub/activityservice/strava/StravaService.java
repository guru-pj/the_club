package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OAuth connect flow and on-demand sync. HTTP calls to Strava are kept outside DB transactions.
 */
@Service
public class StravaService {

    private static final Logger log = LoggerFactory.getLogger(StravaService.class);
    static final String SCOPE = "read,activity:read_all";
    static final Duration SYNC_WINDOW = Duration.ofDays(30);
    static final int SYNC_PAGE_SIZE = 100;

    private final StravaProperties properties;
    private final StravaClient client;
    private final StravaStateSigner stateSigner;
    private final StravaConnectionRepository connections;
    private final StravaTokenManager tokens;
    private final StravaActivityImporter importer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public StravaService(StravaProperties properties, StravaClient client, StravaStateSigner stateSigner,
                         StravaConnectionRepository connections, StravaTokenManager tokens,
                         StravaActivityImporter importer, PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.stateSigner = stateSigner;
        this.connections = connections;
        this.tokens = tokens;
        this.importer = importer;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public String authorizeUrl(UUID userId) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(properties.oauthBaseUrl() + "/authorize")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("approval_prompt", "auto")
                .queryParam("scope", SCOPE)
                .queryParam("state", stateSigner.sign(userId))
                .encode()
                .build()
                .toUriString();
    }

    public StravaConnection completeAuthorization(String code, String state, String scope, String error) {
        UUID userId = stateSigner.verify(state);
        requireConfigured();
        if (error != null && !error.isBlank()) {
            throw ApiException.badRequest("Strava authorization was denied");
        }
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("Missing authorization code");
        }
        StravaTokenResponse token;
        try {
            token = client.exchangeCode(code);
        } catch (RestClientException e) {
            log.warn("Strava code exchange failed: {}", e.getMessage());
            throw ApiException.badGateway("Could not complete Strava authorization");
        }
        if (token == null || token.athlete() == null) {
            throw ApiException.badGateway("Strava did not return athlete details");
        }
        return transactions.execute(status -> {
            long athleteId = token.athlete().id();
            // An athlete can be linked to only one account: the latest successful authorization wins.
            connections.findByAthleteId(athleteId)
                    .filter(other -> !other.getUserId().equals(userId))
                    .ifPresent(other -> {
                        connections.delete(other);
                        connections.flush();
                    });
            StravaConnection connection = connections.findByUserId(userId)
                    .orElseGet(() -> new StravaConnection(userId, athleteId));
            connection.setAthleteId(athleteId);
            connection.setAccessToken(token.accessToken());
            connection.setRefreshToken(token.refreshToken());
            connection.setExpiresAt(Instant.ofEpochSecond(token.expiresAt()));
            connection.setScope(scope);
            return connections.save(connection);
        });
    }

    public Optional<StravaConnection> connection(UUID userId) {
        return connections.findByUserId(userId);
    }

    public void disconnect(UUID userId) {
        connections.findByUserId(userId).ifPresent(connection -> {
            try {
                client.deauthorize(connection.getAccessToken());
            } catch (RestClientException e) {
                log.info("Strava deauthorize failed for user {}; removing connection anyway", userId);
            }
            connections.delete(connection);
        });
    }

    public SyncResult sync(UUID userId) {
        requireConfigured();
        StravaConnection connection = connections.findByUserId(userId)
                .orElseThrow(() -> ApiException.badRequest("Strava account is not connected"));
        List<StravaActivity> recent;
        try {
            String accessToken = tokens.accessToken(connection);
            long after = clock.instant().minus(SYNC_WINDOW).getEpochSecond();
            recent = client.listActivities(accessToken, after, SYNC_PAGE_SIZE);
        } catch (RestClientException e) {
            log.warn("Strava sync failed for user {}: {}", userId, e.getMessage());
            throw ApiException.badGateway("Could not fetch activities from Strava");
        }
        int imported = 0;
        int updated = 0;
        for (StravaActivity activity : recent) {
            if (importer.upsert(userId, activity)) {
                imported++;
            } else {
                updated++;
            }
        }
        return new SyncResult(imported, updated);
    }

    public boolean verifyWebhookToken(String token) {
        String expected = properties.webhookVerifyToken();
        return expected != null && !expected.isBlank() && expected.equals(token);
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Strava integration is not configured");
        }
    }

    public record SyncResult(int imported, int updated) {
    }
}

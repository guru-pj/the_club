package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.model.StravaConnectionStatus;
import com.theclub.activityservice.repository.ActivityRepository;
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
 * OAuth connect flow, settings and on-demand sync. HTTP calls to Strava are kept outside DB transactions
 * (except the locked token refresh in {@link StravaTokenManager}).
 */
@Service
public class StravaService {

    private static final Logger log = LoggerFactory.getLogger(StravaService.class);
    static final String SCOPE = "read,activity:read_all";
    static final int SYNC_PAGE_SIZE = 100;
    /** Upper bound per sync run (100 pages x 100 activities); the next sync continues from where this one got to. */
    static final int MAX_SYNC_PAGES = 100;
    /** Re-read this far before the last sync: activities uploaded late carry an earlier start time. */
    static final Duration SYNC_OVERLAP = Duration.ofDays(7);
    /** A sync marker older than this belongs to a crashed run and is taken over. */
    static final Duration SYNC_STALE_AFTER = Duration.ofMinutes(15);

    private final StravaProperties properties;
    private final StravaClient client;
    private final StravaStateSigner stateSigner;
    private final StravaConnectionRepository connections;
    private final ActivityRepository activities;
    private final StravaTokenManager tokens;
    private final StravaTokenCipher cipher;
    private final StravaActivityImporter importer;
    private final StravaRateLimiter rateLimiter;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public StravaService(StravaProperties properties, StravaClient client, StravaStateSigner stateSigner,
                         StravaConnectionRepository connections, ActivityRepository activities,
                         StravaTokenManager tokens, StravaTokenCipher cipher, StravaActivityImporter importer,
                         StravaRateLimiter rateLimiter, PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.stateSigner = stateSigner;
        this.connections = connections;
        this.activities = activities;
        this.tokens = tokens;
        this.cipher = cipher;
        this.importer = importer;
        this.rateLimiter = rateLimiter;
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
        if (scope != null && !scope.contains("activity:read")) {
            throw ApiException.badRequest("Allow access to your activities on Strava to connect your account");
        }
        StravaTokenResponse token;
        try {
            token = client.exchangeCode(code);
        } catch (StravaRateLimitedException e) {
            throw rateLimited(e, clock);
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
            if (connection.getAthleteId() != athleteId) {
                // Switching to another Strava account: start its history from scratch
                connection.setLastSyncedAt(null);
            }
            connection.setAthleteId(athleteId);
            connection.setAccessToken(cipher.encrypt(token.accessToken()));
            connection.setRefreshToken(cipher.encrypt(token.refreshToken()));
            connection.setExpiresAt(Instant.ofEpochSecond(token.expiresAt()));
            connection.setScope(scope);
            connection.setStatus(StravaConnectionStatus.ACTIVE);
            connection.setRevokedAt(null);
            return connections.save(connection);
        });
    }

    public Optional<StravaConnection> connection(UUID userId) {
        return connections.findByUserId(userId);
    }

    public StravaConnection updateSettings(UUID userId, boolean includePrivate) {
        return transactions.execute(status -> {
            StravaConnection connection = connections.findByUserId(userId)
                    .orElseThrow(() -> ApiException.badRequest("Strava account is not connected"));
            connection.setIncludePrivate(includePrivate);
            return connections.save(connection);
        });
    }

    /** Revokes our access on Strava (best effort) and forgets the tokens. Imported activities are kept. */
    public void disconnect(UUID userId) {
        connections.findByUserId(userId).ifPresent(connection -> {
            if (connection.getStatus() == StravaConnectionStatus.ACTIVE && connection.getAccessToken() != null) {
                try {
                    client.deauthorize(cipher.decrypt(connection.getAccessToken()));
                } catch (RuntimeException e) {
                    log.info("Strava deauthorize failed for user {}; removing connection anyway", userId);
                }
            }
            transactions.executeWithoutResult(status -> connections.deleteById(connection.getId()));
        });
    }

    /** Deletes every activity imported from Strava for this member (data-removal request). */
    public int purgeImportedActivities(UUID userId) {
        Integer deleted = transactions.execute(status ->
                activities.deleteByUserIdAndSource(userId, ActivitySource.STRAVA));
        log.info("Purged {} Strava activities of user {}", deleted, userId);
        return deleted == null ? 0 : deleted;
    }

    /**
     * Imports the member's activities since the last completed sync (minus {@link #SYNC_OVERLAP}), or the last
     * {@code initialSyncDays} on the first sync, page by page. Only one sync per member runs at a time.
     * If Strava's rate limit is reached part-way, the activities imported so far are kept and
     * {@code complete=false} is returned; the next sync picks up again.
     */
    public SyncResult sync(UUID userId) {
        requireConfigured();
        StravaConnection connection = connections.findByUserId(userId)
                .orElseThrow(() -> ApiException.badRequest("Strava account is not connected"));
        if (connection.getStatus() != StravaConnectionStatus.ACTIVE) {
            throw ApiException.badRequest("Your Strava connection has expired. Reconnect Strava to sync.")
                    .withCode("STRAVA_RECONNECT_REQUIRED");
        }
        if (rateLimiter.isPaused()) {
            throw rateLimited(new StravaRateLimitedException(rateLimiter.pausedUntil()), clock);
        }
        Instant startedAt = clock.instant();
        Integer claimed = transactions.execute(status ->
                connections.claimSync(connection.getId(), startedAt, startedAt.minus(SYNC_STALE_AFTER)));
        if (claimed == null || claimed == 0) {
            throw ApiException.conflict("A Strava sync is already running for your account");
        }
        boolean complete = false;
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        try {
            Instant after = connection.getLastSyncedAt() == null
                    ? startedAt.minus(Duration.ofDays(properties.initialSyncDays()))
                    : connection.getLastSyncedAt().minus(SYNC_OVERLAP);
            for (int page = 1; page <= MAX_SYNC_PAGES; page++) {
                int currentPage = page;
                List<StravaActivity> batch = tokens.withAccessToken(connection, token ->
                        client.listActivities(token, after.getEpochSecond(), currentPage, SYNC_PAGE_SIZE));
                for (StravaActivity activity : batch) {
                    if (!activity.ownedBy(connection.getAthleteId()) && activity.athlete() != null) {
                        skipped++;
                        continue;
                    }
                    try {
                        if (importer.upsert(userId, activity) == StravaActivityImporter.Result.CREATED) {
                            imported++;
                        } else {
                            updated++;
                        }
                    } catch (IllegalArgumentException e) {
                        skipped++;
                    }
                }
                if (batch.size() < SYNC_PAGE_SIZE) {
                    complete = true;
                    break;
                }
            }
        } catch (StravaRateLimitedException e) {
            log.info("Strava sync for user {} paused by rate limit after {} activities", userId, imported + updated);
        } catch (StravaAccessRevokedException e) {
            throw ApiException.badRequest("Your Strava connection has expired. Reconnect Strava to sync.")
                    .withCode("STRAVA_RECONNECT_REQUIRED");
        } catch (RestClientException e) {
            log.warn("Strava sync failed for user {}: {}", userId, e.getMessage());
            if (imported + updated == 0) {
                throw ApiException.badGateway("Could not fetch activities from Strava");
            }
        } finally {
            boolean finished = complete;
            transactions.executeWithoutResult(status -> {
                if (finished) {
                    connections.completeSync(connection.getId(), startedAt);
                } else {
                    connections.releaseSync(connection.getId());
                }
            });
        }
        return new SyncResult(imported, updated, skipped, complete);
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

    static ApiException rateLimited(StravaRateLimitedException e, Clock clock) {
        long seconds = Math.max(1, Duration.between(clock.instant(), e.resumeAt()).getSeconds());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Strava's request limit has been reached. Try again in " + Math.max(1, (seconds + 59) / 60) + " minute(s).")
                .withCode("STRAVA_RATE_LIMITED")
                .withHeader("Retry-After", String.valueOf(seconds));
    }

    /**
     * @param complete false when the sync stopped early (rate limit, page cap); run it again to continue
     */
    public record SyncResult(int imported, int updated, int skipped, boolean complete) {
    }
}

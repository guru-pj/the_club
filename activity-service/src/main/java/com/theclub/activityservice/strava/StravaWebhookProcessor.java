package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.model.StravaConnectionStatus;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;
import java.util.Optional;

/**
 * Applies one Strava push event. Called by {@link StravaWebhookWorker}; exceptions mean "retry later".
 * <p>
 * The webhook endpoint is public, so events are treated as hints: activity data is always fetched from Strava
 * with the athlete's own token, and destructive events (delete, deauthorize) are confirmed with Strava first.
 */
@Component
public class StravaWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(StravaWebhookProcessor.class);

    public enum Outcome { DONE, IGNORED }

    private final StravaConnectionRepository connections;
    private final StravaTokenManager tokens;
    private final StravaClient client;
    private final StravaActivityImporter importer;
    private final StravaProperties properties;

    public StravaWebhookProcessor(StravaConnectionRepository connections, StravaTokenManager tokens,
                                  StravaClient client, StravaActivityImporter importer, StravaProperties properties) {
        this.connections = connections;
        this.tokens = tokens;
        this.client = client;
        this.importer = importer;
        this.properties = properties;
    }

    /**
     * @throws StravaRateLimitedException requeue without counting an attempt
     * @throws RuntimeException           any other failure: retry with backoff
     */
    public Outcome process(StravaWebhookEvent event) {
        if (!properties.isConfigured()) {
            return Outcome.IGNORED;
        }
        Optional<StravaConnection> connection = connections.findByAthleteId(event.ownerId());
        if (connection.isEmpty() || connection.get().getStatus() != StravaConnectionStatus.ACTIVE) {
            log.debug("Ignoring Strava event for unknown or disconnected athlete {}", event.ownerId());
            return Outcome.IGNORED;
        }
        StravaConnection owner = connection.get();
        if ("athlete".equals(event.objectType())) {
            Map<String, Object> updates = event.updates();
            Object authorized = updates == null ? null : updates.get("authorized");
            if ("false".equals(String.valueOf(authorized))) {
                confirmDeauthorization(owner);
                return Outcome.DONE;
            }
            return Outcome.IGNORED;
        }
        if (!"activity".equals(event.objectType())) {
            return Outcome.IGNORED;
        }
        try {
            switch (event.aspectType()) {
                case "create", "update" -> {
                    StravaActivity activity = tokens.withAccessToken(owner,
                            token -> client.getActivity(token, event.objectId()));
                    if (activity == null || !activity.ownedBy(owner.getAthleteId())) {
                        return Outcome.IGNORED;
                    }
                    importer.upsert(owner.getUserId(), activity);
                    return Outcome.DONE;
                }
                case "delete" -> {
                    if (activityGone(owner, event.objectId())) {
                        importer.delete(owner.getUserId(), event.objectId());
                    }
                    return Outcome.DONE;
                }
                default -> {
                    return Outcome.IGNORED;
                }
            }
        } catch (StravaAccessRevokedException e) {
            // The connection was marked REVOKED and the member notified; nothing left to do for this event
            return Outcome.IGNORED;
        }
    }

    /** Strava sends a 404 for an activity that really was deleted (or made inaccessible to us). */
    private boolean activityGone(StravaConnection owner, long activityId) {
        try {
            StravaActivity stillThere = tokens.withAccessToken(owner, token -> client.getActivity(token, activityId));
            if (stillThere != null && stillThere.ownedBy(owner.getAthleteId())) {
                importer.upsert(owner.getUserId(), stillThere);
            }
            return false;
        } catch (HttpClientErrorException.NotFound e) {
            return true;
        }
    }

    /**
     * "authorized: false" means the athlete revoked our app on Strava. The event is unauthenticated, so confirm it:
     * a revoked app can no longer refresh tokens. If refreshing still works, the event was not genuine.
     */
    private void confirmDeauthorization(StravaConnection owner) {
        try {
            tokens.verifyAuthorization(owner.getId());
            log.warn("Ignoring Strava deauthorization event for athlete {}: tokens still refresh", owner.getAthleteId());
        } catch (StravaAccessRevokedException e) {
            log.info("Strava athlete {} revoked access", owner.getAthleteId());
        }
    }
}

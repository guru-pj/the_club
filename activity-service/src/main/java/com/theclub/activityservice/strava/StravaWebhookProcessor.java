package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.Optional;

/**
 * Applies Strava webhook events. Strava expects a 200 within 2 seconds, so the controller hands events
 * to {@link #processAsync} and returns immediately.
 * <p>
 * The webhook endpoint is public, so events are treated as hints: activity data is always fetched from Strava
 * with the athlete's own token, and destructive events (delete, deauthorize) are confirmed with Strava first.
 */
@Component
public class StravaWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(StravaWebhookProcessor.class);

    private final StravaConnectionRepository connections;
    private final StravaTokenManager tokens;
    private final StravaClient client;
    private final StravaActivityImporter importer;

    public StravaWebhookProcessor(StravaConnectionRepository connections, StravaTokenManager tokens,
                                  StravaClient client, StravaActivityImporter importer) {
        this.connections = connections;
        this.tokens = tokens;
        this.client = client;
        this.importer = importer;
    }

    @Async
    public void processAsync(StravaWebhookEvent event) {
        try {
            process(event);
        } catch (RuntimeException e) {
            log.warn("Failed to process Strava {} {} event for object {}: {}",
                    event.objectType(), event.aspectType(), event.objectId(), e.getMessage());
        }
    }

    public void process(StravaWebhookEvent event) {
        Optional<StravaConnection> connection = connections.findByAthleteId(event.ownerId());
        if (connection.isEmpty()) {
            log.debug("Ignoring Strava event for unknown athlete {}", event.ownerId());
            return;
        }
        StravaConnection owner = connection.get();
        if ("athlete".equals(event.objectType())) {
            Object authorized = event.updates() == null ? null : event.updates().get("authorized");
            if ("false".equals(String.valueOf(authorized)) && accessRevoked(owner)) {
                connections.delete(owner);
                log.info("Strava athlete {} revoked access; connection removed", event.ownerId());
            }
            return;
        }
        if (!"activity".equals(event.objectType())) {
            return;
        }
        switch (event.aspectType()) {
            case "create", "update" -> {
                StravaActivity activity = client.getActivity(tokens.accessToken(owner), event.objectId());
                if (activity != null && activity.ownedBy(owner.getAthleteId())) {
                    importer.upsert(owner.getUserId(), activity);
                }
            }
            case "delete" -> {
                if (activityGone(owner, event.objectId())) {
                    importer.delete(owner.getUserId(), event.objectId());
                }
            }
            default -> log.debug("Ignoring Strava aspect {}", event.aspectType());
        }
    }

    private boolean activityGone(StravaConnection owner, long activityId) {
        try {
            StravaActivity stillThere = client.getActivity(tokens.accessToken(owner), activityId);
            if (stillThere != null && stillThere.ownedBy(owner.getAthleteId())) {
                importer.upsert(owner.getUserId(), stillThere);
            }
            return false;
        } catch (HttpClientErrorException.NotFound e) {
            return true;
        }
    }

    private boolean accessRevoked(StravaConnection owner) {
        try {
            StravaTokenResponse refreshed = client.refresh(owner.getRefreshToken());
            owner.setAccessToken(refreshed.accessToken());
            owner.setRefreshToken(refreshed.refreshToken());
            owner.setExpiresAt(Instant.ofEpochSecond(refreshed.expiresAt()));
            connections.save(owner);
            return false;
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401;
        }
    }
}

package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Hands out a usable access token, refreshing it shortly before Strava's 6-hour expiry.
 */
@Component
public class StravaTokenManager {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final StravaClient client;
    private final StravaConnectionRepository connections;
    private final Clock clock;

    public StravaTokenManager(StravaClient client, StravaConnectionRepository connections, Clock clock) {
        this.client = client;
        this.connections = connections;
        this.clock = clock;
    }

    public String accessToken(StravaConnection connection) {
        if (connection.getExpiresAt().isAfter(clock.instant().plus(REFRESH_MARGIN))) {
            return connection.getAccessToken();
        }
        StravaTokenResponse refreshed = client.refresh(connection.getRefreshToken());
        connection.setAccessToken(refreshed.accessToken());
        connection.setRefreshToken(refreshed.refreshToken());
        connection.setExpiresAt(Instant.ofEpochSecond(refreshed.expiresAt()));
        connections.save(connection);
        return refreshed.accessToken();
    }
}

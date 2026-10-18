package com.theclub.activityservice.strava;

import java.util.UUID;

/**
 * The athlete's Strava authorization is no longer valid (revoked on Strava, or the refresh token was rejected).
 * The connection has been marked REVOKED and its tokens cleared.
 */
public class StravaAccessRevokedException extends RuntimeException {

    public StravaAccessRevokedException(UUID userId) {
        super("Strava access was revoked for user " + userId);
    }
}

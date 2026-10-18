package com.theclub.activityservice.strava;

import java.time.Instant;

/**
 * Strava's rate limit is (nearly) exhausted; calls are paused until {@link #resumeAt()}. Work should be
 * requeued, not failed.
 */
public class StravaRateLimitedException extends RuntimeException {

    private final transient Instant resumeAt;

    public StravaRateLimitedException(Instant resumeAt) {
        super("Strava rate limit reached; calls paused until " + resumeAt);
        this.resumeAt = resumeAt;
    }

    public Instant resumeAt() {
        return resumeAt;
    }
}

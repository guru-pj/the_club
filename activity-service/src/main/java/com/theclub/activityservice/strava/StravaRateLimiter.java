package com.theclub.activityservice.strava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Tracks Strava's application rate limits from response headers and pauses outbound calls before they are
 * exhausted.
 * <p>
 * Strava sends {@code X-RateLimit-Limit} / {@code X-RateLimit-Usage} ("15-minute,daily", e.g. "200,2000" /
 * "37,512") and, for read endpoints, {@code X-ReadRateLimit-Limit} / {@code X-ReadRateLimit-Usage}. The 15-minute
 * windows reset at :00, :15, :30 and :45 UTC; the daily window resets at midnight UTC. Exceeding a limit returns 429.
 * When usage reaches {@value #PAUSE_THRESHOLD_PERCENT}% of a limit (or on a 429) calls pause until that window resets.
 * <p>
 * The state is per instance; with several replicas each one sees the shared application usage in the headers of
 * its own calls, so all of them back off as the shared budget runs out.
 */
@Component
public class StravaRateLimiter {

    static final int PAUSE_THRESHOLD_PERCENT = 90;
    private static final Logger log = LoggerFactory.getLogger(StravaRateLimiter.class);

    private final Clock clock;
    private final StravaMetrics metrics;
    private volatile Instant pausedUntil = Instant.EPOCH;

    public StravaRateLimiter(Clock clock, StravaMetrics metrics) {
        this.clock = clock;
        this.metrics = metrics;
    }

    /** @throws StravaRateLimitedException while calls are paused */
    public void checkAvailable() {
        Instant until = pausedUntil;
        if (clock.instant().isBefore(until)) {
            throw new StravaRateLimitedException(until);
        }
    }

    public boolean isPaused() {
        return clock.instant().isBefore(pausedUntil);
    }

    public Instant pausedUntil() {
        return pausedUntil;
    }

    /** Reads the rate-limit headers of any Strava response (success or error). */
    public void record(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        evaluate(headers.getFirst("X-RateLimit-Limit"), headers.getFirst("X-RateLimit-Usage"), 0);
        evaluate(headers.getFirst("X-ReadRateLimit-Limit"), headers.getFirst("X-ReadRateLimit-Usage"), 2);
    }

    /** Strava answered 429: pause until the window that ran out resets (daily if the daily usage is spent). */
    public Instant onTooManyRequests(HttpHeaders headers) {
        record(headers);
        Instant now = clock.instant();
        Instant until = dailyExhausted(headers) ? nextDailyReset(now) : nextQuarterHour(now);
        pauseUntil(until);
        return pausedUntil;
    }

    private void evaluate(String limitHeader, String usageHeader, int metricOffset) {
        long[] limit = parse(limitHeader);
        long[] used = parse(usageHeader);
        if (limit == null || used == null) {
            return;
        }
        metrics.rateLimit(metricOffset, used[0], limit[0]);
        metrics.rateLimit(metricOffset + 1, used[1], limit[1]);
        Instant now = clock.instant();
        if (limit[1] > 0 && used[1] * 100 >= limit[1] * PAUSE_THRESHOLD_PERCENT) {
            pauseUntil(nextDailyReset(now));
        } else if (limit[0] > 0 && used[0] * 100 >= limit[0] * PAUSE_THRESHOLD_PERCENT) {
            pauseUntil(nextQuarterHour(now));
        }
    }

    private boolean dailyExhausted(HttpHeaders headers) {
        if (headers == null) {
            return false;
        }
        for (String[] pair : new String[][]{{"X-RateLimit-Limit", "X-RateLimit-Usage"},
                {"X-ReadRateLimit-Limit", "X-ReadRateLimit-Usage"}}) {
            long[] limit = parse(headers.getFirst(pair[0]));
            long[] used = parse(headers.getFirst(pair[1]));
            if (limit != null && used != null && limit[1] > 0 && used[1] >= limit[1]) {
                return true;
            }
        }
        return false;
    }

    private synchronized void pauseUntil(Instant until) {
        if (until.isAfter(pausedUntil)) {
            pausedUntil = until;
            log.warn("Strava rate limit nearly exhausted; pausing Strava calls until {}", until);
        }
    }

    static long[] parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] parts = header.split(",");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(parts[0].trim()), Long.parseLong(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Instant nextQuarterHour(Instant now) {
        Instant hour = now.truncatedTo(ChronoUnit.HOURS);
        long minutes = ChronoUnit.MINUTES.between(hour, now);
        return hour.plus((minutes / 15 + 1) * 15, ChronoUnit.MINUTES);
    }

    static Instant nextDailyReset(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}

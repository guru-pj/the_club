package com.theclub.activityservice.strava;

import com.theclub.activityservice.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StravaRateLimiterTest {

    final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T10:07:30Z"), ZoneOffset.UTC);
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    StravaRateLimiter limiter;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("registry", registry);
        limiter = new StravaRateLimiter(clock, new StravaMetrics(beans.getBeanProvider(
                io.micrometer.core.instrument.MeterRegistry.class)));
    }

    private static HttpHeaders headers(String limit, String usage) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", limit);
        headers.add("X-RateLimit-Usage", usage);
        return headers;
    }

    @Test
    void parsesFifteenMinuteAndDailyValues() {
        assertThat(StravaRateLimiter.parse("200,2000")).containsExactly(200, 2000);
        assertThat(StravaRateLimiter.parse(" 37 , 512 ")).containsExactly(37, 512);
        assertThat(StravaRateLimiter.parse("garbage")).isNull();
        assertThat(StravaRateLimiter.parse(null)).isNull();
    }

    @Test
    void lowUsageKeepsCallsFlowingAndExportsMetrics() {
        limiter.record(headers("200,2000", "37,512"));

        limiter.checkAvailable();
        assertThat(limiter.isPaused()).isFalse();
        assertThat(registry.get("theclub.strava.ratelimit.usage").tags("window", "15min", "kind", "overall")
                .gauge().value()).isEqualTo(37);
        assertThat(registry.get("theclub.strava.ratelimit.limit").tags("window", "daily", "kind", "overall")
                .gauge().value()).isEqualTo(2000);
    }

    @Test
    void ninetyPercentOfTheFifteenMinuteWindowPausesUntilTheNextQuarterHour() {
        limiter.record(headers("200,2000", "180,500"));

        assertThat(limiter.isPaused()).isTrue();
        assertThat(limiter.pausedUntil()).isEqualTo(Instant.parse("2026-10-18T10:15:00Z"));
        assertThatThrownBy(limiter::checkAvailable).isInstanceOf(StravaRateLimitedException.class);

        clock.set(Instant.parse("2026-10-18T10:15:00Z"));
        limiter.checkAvailable();
    }

    @Test
    void nearlyExhaustedDailyLimitPausesUntilMidnightUtc() {
        limiter.record(headers("200,2000", "10,1850"));

        assertThat(limiter.pausedUntil()).isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
    }

    @Test
    void readLimitsAreTrackedToo() {
        HttpHeaders headers = headers("200,2000", "10,100");
        headers.add("X-ReadRateLimit-Limit", "100,1000");
        headers.add("X-ReadRateLimit-Usage", "95,400");

        limiter.record(headers);

        assertThat(limiter.pausedUntil()).isEqualTo(Instant.parse("2026-10-18T10:15:00Z"));
    }

    @Test
    void tooManyRequestsPausesForTheWindowThatRanOut() {
        assertThat(limiter.onTooManyRequests(headers("200,2000", "201,700")))
                .isEqualTo(Instant.parse("2026-10-18T10:15:00Z"));
        assertThat(limiter.onTooManyRequests(headers("200,2000", "50,2000")))
                .isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
    }

    @Test
    void quarterHourBoundaries() {
        assertThat(StravaRateLimiter.nextQuarterHour(Instant.parse("2026-10-18T10:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-18T10:15:00Z"));
        assertThat(StravaRateLimiter.nextQuarterHour(Instant.parse("2026-10-18T10:59:59Z")))
                .isEqualTo(Instant.parse("2026-10-18T11:00:00Z"));
    }
}

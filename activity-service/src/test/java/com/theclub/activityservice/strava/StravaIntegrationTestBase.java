package com.theclub.activityservice.strava;

import com.theclub.activityservice.MutableClock;
import com.theclub.activityservice.client.NotificationClient;
import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.repository.ActivityRepository;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import com.theclub.activityservice.repository.StravaWebhookEventRepository;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Real Spring context and database; Strava's HTTP API and notification-service are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StravaIntegrationTestBase.ClockConfig.class)
abstract class StravaIntegrationTestBase {

    static final Instant NOW = Instant.parse("2026-10-18T10:07:30Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW, ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @Autowired
    MutableClock clock;
    @Autowired
    StravaConnectionRepository connections;
    @Autowired
    ActivityRepository activities;
    @Autowired
    StravaWebhookEventRepository webhookEvents;
    @Autowired
    StravaTokenCipher cipher;
    @Autowired
    StravaRateLimiter rateLimiter;
    @MockitoBean
    StravaClient stravaClient;
    @MockitoBean
    NotificationClient notificationClient;

    @BeforeEach
    void resetState() throws Exception {
        clock.set(NOW);
        webhookEvents.deleteAll();
        activities.deleteAll();
        connections.deleteAll();
        // forget pauses left by earlier tests
        var field = StravaRateLimiter.class.getDeclaredField("pausedUntil");
        field.setAccessible(true);
        field.set(rateLimiter, Instant.EPOCH);
    }

    String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles));
    }

    /** An ACTIVE connection whose access token is valid for {@code validFor}. */
    StravaConnection connect(UUID userId, long athleteId, Duration validFor) {
        StravaConnection connection = new StravaConnection(userId, athleteId);
        connection.setAccessToken(cipher.encrypt("access-0"));
        connection.setRefreshToken(cipher.encrypt("refresh-0"));
        connection.setExpiresAt(clock.instant().plus(validFor));
        connection.setScope("read,activity:read_all");
        return connections.save(connection);
    }
}

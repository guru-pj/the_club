package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.StravaConnection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StravaSyncIntegrationTest extends StravaIntegrationTestBase {

    static final long ATHLETE = 77L;

    private static List<StravaActivity> page(long fromId, int count, long athlete) {
        List<StravaActivity> result = new ArrayList<>();
        LongStream.range(fromId, fromId + count).forEach(id -> result.add(StravaTestData.activity(id, "Run " + id, athlete)));
        return result;
    }

    @Test
    void firstSyncPagesThroughNinetyDaysAndLaterSyncsAreIncremental() throws Exception {
        UUID userId = UUID.randomUUID();
        connect(userId, ATHLETE, Duration.ofDays(3));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(1), eq(100))).thenReturn(page(1, 100, ATHLETE));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(2), eq(100))).thenReturn(page(101, 5, ATHLETE));

        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(105))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.complete").value(true));

        ArgumentCaptor<Long> after = ArgumentCaptor.forClass(Long.class);
        verify(stravaClient, org.mockito.Mockito.times(2)).listActivities(eq("access-0"), after.capture(), anyInt(), eq(100));
        assertThat(after.getAllValues()).containsOnly(NOW.minus(Duration.ofDays(90)).getEpochSecond());
        StravaConnection stored = connections.findByUserId(userId).orElseThrow();
        assertThat(stored.getLastSyncedAt()).isEqualTo(NOW);
        assertThat(stored.getSyncStartedAt()).isNull();

        // Second sync, a day later: starts from the last sync minus the 7-day overlap and updates in place
        clock.advance(Duration.ofDays(1));
        org.mockito.Mockito.reset(stravaClient);
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(1), eq(100))).thenReturn(page(104, 3, ATHLETE));
        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.updated").value(2));
        verify(stravaClient).listActivities("access-0", NOW.minus(Duration.ofDays(7)).getEpochSecond(), 1, 100);
    }

    @Test
    void activitiesOfAnotherAthleteAreSkipped() throws Exception {
        UUID userId = UUID.randomUUID();
        connect(userId, ATHLETE, Duration.ofHours(5));
        List<StravaActivity> mixed = new ArrayList<>(page(1, 2, ATHLETE));
        mixed.addAll(page(3, 1, 999L));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(1), eq(100))).thenReturn(mixed);

        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void onlyOneSyncPerMemberRunsAtATime() throws Exception {
        UUID userId = UUID.randomUUID();
        StravaConnection connection = connect(userId, ATHLETE, Duration.ofHours(5));
        connection.setSyncStartedAt(NOW.minus(Duration.ofMinutes(2)));
        connections.save(connection);

        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(status().isConflict());

        // A marker left by a crashed run is taken over after 15 minutes
        clock.advance(Duration.ofMinutes(20));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(1), eq(100))).thenReturn(List.of());
        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true));
    }

    @Test
    void rateLimitMidSyncKeepsProgressAndDoesNotAdvanceTheCursor() throws Exception {
        UUID userId = UUID.randomUUID();
        connect(userId, ATHLETE, Duration.ofHours(5));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(1), eq(100))).thenReturn(page(1, 100, ATHLETE));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(2), eq(100)))
                .thenThrow(new StravaRateLimitedException(Instant.parse("2026-10-18T10:15:00Z")));

        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(100))
                .andExpect(jsonPath("$.complete").value(false));

        StravaConnection stored = connections.findByUserId(userId).orElseThrow();
        assertThat(stored.getLastSyncedAt()).isNull();
        assertThat(stored.getSyncStartedAt()).isNull();
    }

    @Test
    void syncWhilePausedAnswers503WithRetryAfter() throws Exception {
        UUID userId = UUID.randomUUID();
        connect(userId, ATHLETE, Duration.ofHours(5));
        rateLimiter.onTooManyRequests(null);

        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "450"))
                .andExpect(jsonPath("$.code").value("STRAVA_RATE_LIMITED"));
    }

    @Test
    void revokedConnectionAsksTheMemberToReconnect() throws Exception {
        UUID userId = UUID.randomUUID();
        StravaConnection connection = connect(userId, ATHLETE, Duration.ofHours(5));
        connection.setStatus(com.theclub.activityservice.model.StravaConnectionStatus.REVOKED);
        connections.save(connection);

        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STRAVA_RECONNECT_REQUIRED"));
        mvc.perform(get("/api/v1/strava/connection").header("Authorization", token(userId, "USER")))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void privateActivitiesCountForClubsOnlyAfterOptIn() throws Exception {
        UUID userId = UUID.randomUUID();
        connect(userId, ATHLETE, Duration.ofHours(5));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(1), eq(100))).thenReturn(List.of(
                StravaTestData.activity(1, "Public run", ATHLETE, false),
                StravaTestData.activity(2, "Private run", ATHLETE, true)));
        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")))
                .andExpect(jsonPath("$.imported").value(2));

        String stats = """
                {"userIds":["%s"]}""".formatted(userId);
        mvc.perform(post("/internal/activities/stats").header("X-Internal-Key", "test-internal-key")
                        .contentType("application/json").content(stats))
                .andExpect(jsonPath("$[0].sessions").value(1))
                .andExpect(jsonPath("$[0].totalDistanceKm").value(10.5));

        mvc.perform(put("/api/v1/strava/connection/settings").header("Authorization", token(userId, "USER"))
                        .contentType("application/json").content("{\"includePrivate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includePrivate").value(true));
        mvc.perform(post("/internal/activities/stats").header("X-Internal-Key", "test-internal-key")
                        .contentType("application/json").content(stats))
                .andExpect(jsonPath("$[0].sessions").value(2));
    }

    @Test
    void membersCanPurgeTheirImportedActivities() throws Exception {
        UUID userId = UUID.randomUUID();
        connect(userId, ATHLETE, Duration.ofHours(5));
        when(stravaClient.listActivities(eq("access-0"), anyLong(), eq(1), eq(100))).thenReturn(page(1, 3, ATHLETE));
        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(userId, "USER")));

        mvc.perform(delete("/api/v1/strava/activities").header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(3));
        assertThat(activities.findAll()).noneMatch(a -> a.getSource() == ActivitySource.STRAVA
                && a.getUserId().equals(userId));
    }
}

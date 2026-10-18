package com.theclub.activityservice.controller;

import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.ActivityType;
import com.theclub.activityservice.repository.ActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalActivityControllerIntegrationTest {

    private static final String KEY = "test-internal-key";

    @Autowired
    MockMvc mvc;
    @Autowired
    ActivityRepository activities;

    private Activity save(UUID userId, double km, int seconds, LocalDateTime start) {
        Activity activity = new Activity(userId, ActivitySource.MANUAL);
        activity.setTitle("Run");
        activity.setType(ActivityType.RUN);
        activity.setDistance(km);
        activity.setDuration(seconds);
        activity.setStartTime(start);
        activity.setEndTime(start.plusSeconds(seconds));
        return activities.save(activity);
    }

    @Test
    void aggregatesPerUserWithinRangeAndOmitsInactiveUsers() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        save(a, 5.0, 1500, LocalDateTime.of(2026, 10, 1, 6, 0));
        save(a, 10.0, 3000, LocalDateTime.of(2026, 10, 15, 6, 0));
        save(a, 21.1, 7200, LocalDateTime.of(2026, 9, 30, 6, 0));   // before range
        save(b, 3.0, 1200, LocalDateTime.of(2026, 10, 31, 23, 59));
        save(b, 8.0, 2400, LocalDateTime.of(2026, 11, 1, 0, 0));    // "to" is exclusive

        mvc.perform(post("/internal/activities/stats").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s","%s","%s"],"from":"2026-10-01T00:00:00","to":"2026-11-01T00:00:00"}"""
                                .formatted(a, b, idle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.userId=='%s')].totalDistanceKm".formatted(a)).value(15.0))
                .andExpect(jsonPath("$[?(@.userId=='%s')].totalDurationSeconds".formatted(a)).value(4500))
                .andExpect(jsonPath("$[?(@.userId=='%s')].sessions".formatted(a)).value(2))
                .andExpect(jsonPath("$[?(@.userId=='%s')].sessions".formatted(b)).value(1));

        mvc.perform(post("/internal/activities/stats").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[\"%s\"]}".formatted(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessions").value(3));

        mvc.perform(post("/internal/activities/stats").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getActivityAndRequireInternalKey() throws Exception {
        Activity saved = save(UUID.randomUUID(), 5.0, 1500, LocalDateTime.of(2026, 10, 1, 6, 0));

        mvc.perform(get("/internal/activities/{id}", saved.getId()).header("X-Internal-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(saved.getUserId().toString()))
                .andExpect(jsonPath("$.source").value("MANUAL"));
        mvc.perform(get("/internal/activities/{id}", UUID.randomUUID()).header("X-Internal-Key", KEY))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/activities/{id}", saved.getId()).header("X-Internal-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }
}

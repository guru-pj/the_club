package com.theclub.activityservice.controller;

import tools.jackson.databind.ObjectMapper;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivityControllerIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles));
    }

    private static String body(String title, LocalDateTime start) {
        return """
                {"userId":"%s","title":"%s","type":"RUN","distance":5.2,"duration":1800,"startTime":"%s"}"""
                .formatted(UUID.randomUUID(), title, start.truncatedTo(ChronoUnit.SECONDS));
    }

    private String create(UUID userId, String title, LocalDateTime start) throws Exception {
        String response = mvc.perform(post("/api/v1/activities").header("Authorization", token(userId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(title, start)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    @Test
    void createIgnoresBodyUserIdAndListsNewestFirst() throws Exception {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        create(userId, "Older", now.minusDays(2));
        create(userId, "Newer", now.minusHours(1));

        mvc.perform(get("/api/v1/activities/me").header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Newer"))
                .andExpect(jsonPath("$.items[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.items[0].source").value("MANUAL"));

        mvc.perform(get("/api/v1/activities/user/{id}", userId).header("Authorization", token(UUID.randomUUID(), "USER"))
                        .param("from", now.minusDays(1).truncatedTo(ChronoUnit.SECONDS).toString())
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Newer"));
    }

    @Test
    void validationErrors() throws Exception {
        mvc.perform(post("/api/v1/activities").header("Authorization", token(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"distance\":-1,\"duration\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.distance").exists())
                .andExpect(jsonPath("$.errors.duration").exists())
                .andExpect(jsonPath("$.errors.startTime").exists());

        mvc.perform(post("/api/v1/activities").header("Authorization", token(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Future", LocalDateTime.now().plusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOwnerCanUpdateAndOwnerOrAdminCanDelete() throws Exception {
        UUID owner = UUID.randomUUID();
        String id = create(owner, "Run", LocalDateTime.now().minusHours(2));
        UUID stranger = UUID.randomUUID();

        mvc.perform(put("/api/v1/activities/{id}", id).header("Authorization", token(stranger, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Hacked", LocalDateTime.now().minusHours(2))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/activities/{id}", id).header("Authorization", token(owner, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Edited", LocalDateTime.now().minusHours(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited"));
        mvc.perform(delete("/api/v1/activities/{id}", id).header("Authorization", token(stranger, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/activities/{id}", id).header("Authorization", token(stranger, "USER", "ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/activities/{id}", id).header("Authorization", token(owner, "USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/activities/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Run", LocalDateTime.now().minusHours(1))))
                .andExpect(status().isUnauthorized());
    }
}

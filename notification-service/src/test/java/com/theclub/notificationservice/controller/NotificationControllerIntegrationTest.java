package com.theclub.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    private static final String KEY = "test-internal-key";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;

    private String tokenFor(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles));
    }

    private String createInternal(UUID userId, String message, boolean withEmail) throws Exception {
        String email = withEmail
                ? ",\"email\":{\"to\":\"x@example.com\",\"subject\":\"Hi\",\"body\":\"<p>Hi</p>\"}"
                : "";
        String body = mvc.perform(post("/internal/notifications").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"type\":\"TEST\",\"message\":\"%s\"%s}".formatted(userId, message, email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    @Test
    void internalCreateRequiresKey() throws Exception {
        UUID userId = UUID.randomUUID();
        mvc.perform(post("/internal/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"type\":\"T\",\"message\":\"m\"}".formatted(userId)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/notifications").header("Authorization", tokenFor(userId, "USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"type\":\"T\",\"message\":\"m\"}".formatted(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createListMarkReadAndCount() throws Exception {
        UUID userId = UUID.randomUUID();
        String first = createInternal(userId, "first", false);
        createInternal(userId, "second", true);
        String token = tokenFor(userId, "USER");

        mvc.perform(get("/api/v1/notifications/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].message").value("second"))
                .andExpect(jsonPath("$.items[1].message").value("first"));
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", token))
                .andExpect(jsonPath("$.unread").value(2));

        mvc.perform(patch("/api/v1/notifications/{id}/read", first).header("Authorization", tokenFor(UUID.randomUUID(), "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notifications/{id}/read", first).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", token))
                .andExpect(jsonPath("$.unread").value(1));

        mvc.perform(post("/api/v1/notifications/me/read-all").header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", token))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void cannotListAnotherUsersNotifications() throws Exception {
        UUID userId = UUID.randomUUID();
        createInternal(userId, "private", false);

        mvc.perform(get("/api/v1/notifications/user/{id}", userId).header("Authorization", tokenFor(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications/user/{id}", userId).header("Authorization", tokenFor(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/notifications/user/{id}", userId).header("Authorization", tokenFor(UUID.randomUUID(), "USER", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminOnlyCreate() throws Exception {
        String body = "{\"userId\":\"%s\",\"type\":\"ANNOUNCEMENT\",\"message\":\"Hello\"}".formatted(UUID.randomUUID());
        mvc.perform(post("/api/v1/notifications").header("Authorization", tokenFor(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/notifications").header("Authorization", tokenFor(UUID.randomUUID(), "USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidEmailPayloadIsRejected() throws Exception {
        mvc.perform(post("/internal/notifications").header("X-Internal-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"type\":\"T\",\"message\":\"m\",\"email\":{\"to\":\"not-an-email\",\"subject\":\"s\",\"body\":\"b\"}}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}

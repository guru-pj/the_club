package com.theclub.activityservice.strava;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StravaControllerIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;
    @MockitoBean
    StravaClient stravaClient;

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles));
    }

    @Test
    void webhookVerificationEchoesChallengeOnlyForValidToken() throws Exception {
        mvc.perform(get("/api/v1/strava/webhook").param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test-verify-token").param("hub.challenge", "abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['hub.challenge']").value("abc123"));
        mvc.perform(get("/api/v1/strava/webhook").param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong").param("hub.challenge", "abc123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookEventsAreAcceptedWithoutAuthentication() throws Exception {
        mvc.perform(post("/api/v1/strava/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"object_type":"activity","object_id":1,"aspect_type":"create","owner_id":999,
                                 "updates":{},"event_time":1760000000,"subscription_id":1}"""))
                .andExpect(status().isOk());
    }

    @Test
    void connectFlowStoresConnection() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = mvc.perform(get("/api/v1/strava/connect").header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String authorizeUrl = json.readTree(body).get("authorizeUrl").asText();
        String state = UriComponentsBuilder.fromUriString(authorizeUrl).build().getQueryParams().getFirst("state");

        when(stravaClient.exchangeCode("the-code")).thenReturn(new StravaTokenResponse("access", "refresh",
                Instant.now().plusSeconds(21600).getEpochSecond(), new StravaTokenResponse.Athlete(777L)));

        mvc.perform(get("/api/v1/strava/callback").param("code", "the-code").param("state", state)
                        .param("scope", "read,activity:read_all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.athleteId").value(777));

        mvc.perform(get("/api/v1/strava/connection").header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.athleteId").value(777));

        mvc.perform(delete("/api/v1/strava/connection").header("Authorization", token(userId, "USER")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/strava/connection").header("Authorization", token(userId, "USER")))
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void callbackRejectsForgedState() throws Exception {
        mvc.perform(get("/api/v1/strava/callback").param("code", "c").param("state", "forged.state"))
                .andExpect(status().isForbidden());
    }

    @Test
    void stravaActivitiesAreVisibleOnlyToSelfOrAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        mvc.perform(get("/api/v1/strava/activities/{id}", userId).header("Authorization", token(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/strava/activities/{id}", userId).header("Authorization", token(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void syncRequiresConnection() throws Exception {
        mvc.perform(post("/api/v1/strava/sync").header("Authorization", token(UUID.randomUUID(), "USER")))
                .andExpect(status().isBadRequest());
    }
}

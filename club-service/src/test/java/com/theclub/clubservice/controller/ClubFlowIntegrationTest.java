package com.theclub.clubservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.client.NotificationClient;
import com.theclub.clubservice.client.UserClient;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClubFlowIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;

    @MockitoBean
    UserClient userClient;
    @MockitoBean
    ActivityClient activityClient;
    @MockitoBean
    EventClient eventClient;
    @MockitoBean
    NotificationClient notificationClient;

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, id + "@example.com", "User " + id.toString().substring(0, 4), List.of(roles));
    }

    private JsonNode createClub(UUID ownerId, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/clubs").header("Authorization", token(ownerId, "USER", "CLUB_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clubName":"%s","description":"Weekend running club","city":"Chennai","clubType":"RUNNING","ownerId":"%s"}"""
                                .formatted(name, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void plainUserCannotCreateClub() throws Exception {
        mvc.perform(post("/api/v1/clubs").header("Authorization", token(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only verified club owners can create clubs"));
    }

    @Test
    void createJoinLeaveAndList() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        // The H2 database is shared by every test class, so the name (and the search below) must be unique to this run
        String suffix = owner.toString().substring(0, 8);
        String name = "Chennai Runners " + suffix;
        String clubId = createClub(owner, name).get("id").asText();

        mvc.perform(post("/api/v1/clubs").header("Authorization", token(owner, "USER", "CLUB_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name.toLowerCase())))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", token(member, "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", token(member, "USER")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", token(member, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
        mvc.perform(get("/api/v1/clubs/user/{id}", member).header("Authorization", token(owner, "USER")))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/clubs/owner/{id}", owner).header("Authorization", token(member, "USER")))
                .andExpect(jsonPath("$[0].name").value(name));
        mvc.perform(get("/api/v1/clubs").param("q", ("unners " + suffix).toUpperCase()).header("Authorization", token(member, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        when(userClient.summaries(any())).thenReturn(List.of(new UserClient.UserSummary(member, "Meera", null)));
        mvc.perform(get("/api/v1/clubs/{id}/members", clubId).header("Authorization", token(member, "USER")))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[1].name").value("Meera"));

        mvc.perform(delete("/api/v1/clubs/{id}/members/me", clubId).header("Authorization", token(owner, "USER", "CLUB_OWNER")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/clubs/{id}/members/me", clubId).header("Authorization", token(member, "USER")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/clubs/me").header("Authorization", token(member, "USER")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ownerPostsToFeedWithStravaActivity() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        String clubId = createClub(owner, "Feed Club").get("id").asText();
        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", token(member, "USER")));

        UUID activityId = UUID.randomUUID();
        when(activityClient.get(activityId)).thenReturn(new ActivityClient.ActivityDetails(activityId, owner, "Long Run",
                "RUN", 21.1, 7200, 1500, LocalDateTime.of(2026, 10, 18, 5, 30), null, "STRAVA", "987"));

        String post = """
                {"title":"Sunday long run","content":"21k done","photoUrls":["https://img/1.jpg"],"activityId":"%s"}"""
                .formatted(activityId);
        mvc.perform(post("/api/v1/clubs/{id}/posts", clubId).header("Authorization", token(member, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clubs/{id}/posts", clubId).header("Authorization", token(owner, "USER", "CLUB_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ANNOUNCEMENT"))
                .andExpect(jsonPath("$.activity.distanceKm").value(21.1));

        verify(notificationClient, atLeastOnce()).create(any());

        mvc.perform(get("/api/v1/clubs/{id}/posts", clubId).header("Authorization", token(member, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Sunday long run"))
                .andExpect(jsonPath("$.items[0].photoUrls[0]").value("https://img/1.jpg"));
    }

    @Test
    void leaderboardRanksMembers() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        String clubId = createClub(owner, "Board Club").get("id").asText();
        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", token(member, "USER")));
        when(activityClient.stats(any())).thenReturn(List.of(new ActivityClient.ActivityStats(member, 42.2, 14400, 3)));

        mvc.perform(get("/api/v1/clubs/{id}/leaderboard", clubId).param("metric", "SESSIONS")
                        .header("Authorization", token(member, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("SESSIONS"))
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.entries[0].userId").value(member.toString()))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[1].userId").value(owner.toString()))
                .andExpect(jsonPath("$.entries[1].sessions").value(0));
    }

    @Test
    void internalEndpoints() throws Exception {
        UUID owner = UUID.randomUUID();
        String clubId = createClub(owner, "Internal Club").get("id").asText();

        mvc.perform(get("/internal/clubs/{id}", clubId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/clubs/{id}", clubId).header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.memberCount").value(1));
        mvc.perform(get("/internal/clubs/{id}/members/{u}", clubId, owner).header("X-Internal-Key", "test-internal-key"))
                .andExpect(jsonPath("$.member").value(true))
                .andExpect(jsonPath("$.role").value("OWNER"));
        mvc.perform(get("/internal/clubs/{id}/members/{u}", clubId, UUID.randomUUID()).header("X-Internal-Key", "test-internal-key"))
                .andExpect(jsonPath("$.member").value(false));
        mvc.perform(post("/internal/clubs/summaries").header("X-Internal-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"%s\"]}".formatted(clubId)))
                .andExpect(jsonPath("$[0].name").value("Internal Club"));
        mvc.perform(get("/internal/clubs/{id}", UUID.randomUUID()).header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isNotFound());
    }
}

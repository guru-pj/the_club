package com.theclub.clubservice.controller;

import tools.jackson.databind.ObjectMapper;
import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.client.NotificationClient;
import com.theclub.clubservice.client.UserClient;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationIntegrationTest {

    private static final String APPLICATION = """
            {"clubName":"%s","clubType":"RUNNING","city":"Chennai",
             "socialLinks":{"instagram":"https://instagram.com/club"},
             "proofFiles":["https://files.example.com/proof.jpg"],
             "description":"We run every Sunday","contactPhone":"+91 9876543210"}""";

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

    private final UUID adminId = UUID.randomUUID();

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles));
    }

    private String apply(UUID userId, String clubName) throws Exception {
        String body = mvc.perform(post("/api/v1/owner-verification/apply").header("Authorization", token(userId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION.formatted(clubName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("requestId").asText();
    }

    @Test
    void applyApproveCreatesClub() throws Exception {
        UUID applicant = UUID.randomUUID();
        String requestId = apply(applicant, "Approved Runners");

        mvc.perform(post("/api/v1/owner-verification/apply").header("Authorization", token(applicant, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION.formatted("Again")))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/owner-verification/requests").param("status", "PENDING")
                        .header("Authorization", token(applicant, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/owner-verification/requests").param("status", "PENDING")
                        .header("Authorization", token(adminId, "USER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(requestId)).exists());

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", token(applicant, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"createClub\":true}"))
                .andExpect(status().isForbidden());

        String approved = mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", token(adminId, "USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"createClub\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdClubId").exists())
                .andReturn().getResponse().getContentAsString();
        verify(userClient).addRole(eq(applicant), eq(new UserClient.RoleRequest("CLUB_OWNER")));

        String clubId = json.readTree(approved).get("createdClubId").asText();
        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", token(applicant, "USER", "CLUB_OWNER")))
                .andExpect(jsonPath("$.name").value("Approved Runners"))
                .andExpect(jsonPath("$.ownerId").value(applicant.toString()))
                .andExpect(jsonPath("$.myRole").value("OWNER"));

        mvc.perform(get("/api/v1/owner-verification/requests/{id}/history", requestId)
                        .header("Authorization", token(adminId, "USER", "ADMIN")))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void approvalRollsBackWhenUserServiceFails() throws Exception {
        UUID applicant = UUID.randomUUID();
        String requestId = apply(applicant, "Rollback Runners");
        when(userClient.addRole(any(), any())).thenThrow(new RuntimeException("user-service down"));

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", token(adminId, "USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"createClub\":true}"))
                .andExpect(status().isBadGateway());

        mvc.perform(get("/api/v1/owner-verification/requests/{id}", requestId).header("Authorization", token(applicant, "USER")))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/v1/clubs").param("q", "Rollback").header("Authorization", token(applicant, "USER")))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void needsInfoUploadAndRejectThenReapply() throws Exception {
        UUID applicant = UUID.randomUUID();
        String requestId = apply(applicant, "Info Runners");
        String admin = token(adminId, "USER", "ADMIN");

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/request-info", requestId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Add Strava club link\"}"))
                .andExpect(jsonPath("$.status").value("NEEDS_INFO"));

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/upload-proof", requestId)
                        .header("Authorization", token(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"proofFiles\":[\"https://f/x.png\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/owner-verification/requests/{id}/upload-proof", requestId)
                        .header("Authorization", token(applicant, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"proofFiles\":[\"https://f/x.png\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.proofFiles.length()").value(2));

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/reject", requestId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/owner-verification/requests/{id}/reject", requestId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not enough proof\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.adminNotes").value("Not enough proof"));

        apply(applicant, "Info Runners");
        mvc.perform(get("/api/v1/owner-verification/requests/me").header("Authorization", token(applicant, "USER")))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void ownersCannotApply() throws Exception {
        mvc.perform(post("/api/v1/owner-verification/apply").header("Authorization", token(UUID.randomUUID(), "USER", "CLUB_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION.formatted("Owner Club")))
                .andExpect(status().isConflict());
    }
}

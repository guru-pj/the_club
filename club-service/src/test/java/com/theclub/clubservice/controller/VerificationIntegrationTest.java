package com.theclub.clubservice.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.client.NotificationClient;
import com.theclub.clubservice.client.UserClient;
import com.theclub.common.media.MediaStore;
import com.theclub.common.media.StoredDocument;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Club owner journey: one application per club, private documents, changes required and resubmission, approval. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationIntegrationTest {

    private static final String APPLICATION = """
            {"clubName":"%s","clubType":"RUNNING","city":"Chennai",
             "socialLinks":{"instagram":"https://instagram.com/club"},
             "proofFiles":["https://files.example.com/proof.jpg"],
             "description":"We run every Sunday","contactPhone":"+91 9876543210",
             "contactEmail":"hello@club.example","address":"Lighthouse, Marina Beach",
             "latitude":13.05,"longitude":80.28,"website":"https://club.example"}""";

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
    @MockitoBean
    MediaStore media;

    private final UUID adminId = UUID.randomUUID();

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles));
    }

    private String admin() {
        return token(adminId, "USER", "ADMIN");
    }

    /** Club names are unique per test: every test class shares one H2 database. */
    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String apply(UUID userId, String clubName, String... roles) throws Exception {
        String body = mvc.perform(post("/api/v1/owner-verification/apply")
                        .header("Authorization", token(userId, roles.length == 0 ? new String[]{"USER"} : roles))
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION.formatted(clubName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("requestId").asString();
    }

    private JsonNode approve(String requestId, String officialName) throws Exception {
        String body = officialName == null ? "{}" : "{\"clubPayload\":{\"name\":\"%s\"}}".formatted(officialName);
        return json.readTree(mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void approvalCreatesTheClubWithItsVerifiedNameAndDetails() throws Exception {
        UUID applicant = UUID.randomUUID();
        String name = unique("Approved Runners");
        String requestId = apply(applicant, name);

        mvc.perform(post("/api/v1/owner-verification/apply").header("Authorization", token(applicant, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION.formatted(name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_APPLICATION"));
        mvc.perform(get("/api/v1/owner-verification/requests").param("status", "PENDING")
                        .header("Authorization", token(applicant, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/owner-verification/requests").param("status", "PENDING").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(requestId)).exists());
        mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", token(applicant, "USER")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        String official = unique("Approved Runners Club");
        // the admin form sends empty strings for fields it leaves blank
        mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clubPayload\":{\"name\":\"%s\",\"website\":\"not a link\"}}".formatted(official)))
                .andExpect(status().isBadRequest());
        String clubId = json.readTree(mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clubPayload\":{\"name\":\"%s\",\"website\":\"https://club.example\",\"contactPhone\":\"\",\"rules\":\"\",\"socialLinks\":{\"instagram\":\"https://instagram.com/club\",\"facebook\":\"\"}}}"
                                .formatted(official)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("createdClubId").asString();
        verify(userClient).addRole(eq(applicant), eq(new UserClient.RoleRequest("CLUB_OWNER")));
        verify(notificationClient).create(argThat(n -> n.userId().equals(applicant) && n.type().equals("OWNER_VERIFICATION")));

        String owner = token(applicant, "USER", "CLUB_OWNER");
        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", owner))
                .andExpect(jsonPath("$.name").value(official))
                .andExpect(jsonPath("$.ownerId").value(applicant.toString()))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.contactEmail").value("hello@club.example"))
                .andExpect(jsonPath("$.website").value("https://club.example"))
                .andExpect(jsonPath("$.contactPhone").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.socialLinks.instagram").value("https://instagram.com/club"))
                .andExpect(jsonPath("$.latitude").value(13.05));
        // the link to the club stays on the application
        mvc.perform(get("/api/v1/owner-verification/requests/me").header("Authorization", owner))
                .andExpect(jsonPath("$[0].createdClubId").value(clubId));

        // the owner completes the profile, but the verified name only changes through an admin
        mvc.perform(put("/api/v1/clubs/{id}", clubId).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":\"Lights on after dark\",\"contactPhone\":\"+91 90000 00000\","
                                + "\"socialLinks\":{\"instagram\":\"https://instagram.com/club\",\"facebook\":\"https://facebook.com/club\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules").value("Lights on after dark"))
                .andExpect(jsonPath("$.socialLinks.facebook").value("https://facebook.com/club"));
        mvc.perform(put("/api/v1/clubs/{id}", clubId).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Something else\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFIED_NAME"));

        mvc.perform(get("/api/v1/owner-verification/requests/{id}/history", requestId).header("Authorization", admin()))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void eachClubIsVerifiedOnItsOwn() throws Exception {
        UUID applicant = UUID.randomUUID();
        String first = apply(applicant, unique("First Club"));
        String second = apply(applicant, unique("Second Club"));
        approve(first, null);

        // owning one verified club gives no way to set up another without its own approval
        String owner = token(applicant, "USER", "CLUB_OWNER");
        mvc.perform(post("/api/v1/clubs").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(unique("Shortcut Club"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CLUB_VERIFICATION_REQUIRED"));
        mvc.perform(get("/api/v1/owner-verification/requests/{id}", second).header("Authorization", owner))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdClubId").doesNotExist());
        // a verified owner applies for a further club like anyone else
        apply(applicant, unique("Third Club"), "USER", "CLUB_OWNER");
    }

    @Test
    void clubsAlreadyOnTheClubCannotBeClaimed() throws Exception {
        String name = unique("Taken Club");
        approve(apply(UUID.randomUUID(), name), null);

        mvc.perform(post("/api/v1/owner-verification/apply").header("Authorization", token(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION.formatted(name.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLUB_EXISTS"));
    }

    @Test
    void approvalRollsBackWhenUserServiceFails() throws Exception {
        UUID applicant = UUID.randomUUID();
        String name = unique("Rollback Runners");
        String requestId = apply(applicant, name);
        when(userClient.addRole(any(), any())).thenThrow(new RuntimeException("user-service down"));

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/approve", requestId)
                        .header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadGateway());

        mvc.perform(get("/api/v1/owner-verification/requests/{id}", requestId).header("Authorization", token(applicant, "USER")))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/v1/clubs").param("q", name).header("Authorization", token(applicant, "USER")))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void changesRequiredThenUpdateAndResubmit() throws Exception {
        UUID applicant = UUID.randomUUID();
        String name = unique("Info Runners");
        String requestId = apply(applicant, name);
        String me = token(applicant, "USER");

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/request-info", requestId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Add the club's Facebook page\"}"))
                .andExpect(jsonPath("$.status").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.adminNotes").value("Add the club's Facebook page"));
        verify(notificationClient).create(argThat(n -> n.userId().equals(applicant) && n.message().contains("Facebook")));

        String updated = APPLICATION.formatted(name).replace("\"socialLinks\":{\"instagram\":\"https://instagram.com/club\"}",
                "\"socialLinks\":{\"instagram\":\"https://instagram.com/club\",\"facebook\":\"https://facebook.com/club\"}");
        mvc.perform(put("/api/v1/owner-verification/requests/{id}", requestId).header("Authorization", token(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/owner-verification/requests/{id}", requestId).header("Authorization", me)
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialLinks.facebook").value("https://facebook.com/club"))
                .andExpect(jsonPath("$.status").value("NEEDS_INFO"));
        mvc.perform(post("/api/v1/owner-verification/requests/{id}/resubmit", requestId).header("Authorization", me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Added the Facebook page\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/v1/owner-verification/requests/{id}/resubmit", requestId).header("Authorization", me))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/owner-verification/requests/{id}/reject", requestId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/owner-verification/requests/{id}/reject", requestId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not enough proof\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        // a rejected application can't be changed, but a new one can be made
        mvc.perform(put("/api/v1/owner-verification/requests/{id}", requestId).header("Authorization", me)
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isConflict());
        apply(applicant, name);
        mvc.perform(get("/api/v1/owner-verification/requests/me").header("Authorization", me))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void proofDocumentsArePrivateToTheApplicantAndAdmins() throws Exception {
        UUID applicant = UUID.randomUUID();
        String requestId = apply(applicant, unique("Document Club"));
        String me = token(applicant, "USER");
        byte[] pdf = "%PDF-1.7 proof".getBytes(StandardCharsets.US_ASCII);
        String key = "private/verification/" + requestId + "/doc.pdf";
        when(media.storePrivate(eq("verification/" + requestId), any()))
                .thenReturn(new StoredDocument(key, "application/pdf", pdf.length));
        when(media.readPrivate(key)).thenReturn(Optional.of(pdf));
        MockMultipartFile file = new MockMultipartFile("file", "Club registration <2024>.pdf", "application/pdf", pdf);

        mvc.perform(multipart("/api/v1/owner-verification/requests/{id}/documents", requestId).file(file)
                        .header("Authorization", token(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden());
        String documentId = json.readTree(mvc.perform(multipart("/api/v1/owner-verification/requests/{id}/documents", requestId)
                        .file(file).header("Authorization", me))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Club registration _2024_.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        mvc.perform(get("/api/v1/owner-verification/requests/{id}", requestId).header("Authorization", admin()))
                .andExpect(jsonPath("$.documents[0].id").value(documentId));
        String download = "/api/v1/owner-verification/requests/" + requestId + "/documents/" + documentId;
        byte[] body = mvc.perform(get(download).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(pdf);
        mvc.perform(get(download).header("Authorization", me)).andExpect(status().isOk());
        mvc.perform(get(download).header("Authorization", token(UUID.randomUUID(), "USER"))).andExpect(status().isForbidden());
        mvc.perform(get(download)).andExpect(status().isUnauthorized());

        mvc.perform(delete(download).header("Authorization", admin())).andExpect(status().isForbidden());
        mvc.perform(delete(download).header("Authorization", me)).andExpect(status().isNoContent());
        verify(media).deletePrivate(key);
    }
}

package com.theclub.clubservice.controller;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.client.NotificationClient;
import com.theclub.clubservice.client.UserClient;
import com.theclub.common.media.MediaStore;
import com.theclub.common.media.StoredImage;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Club group chat (subscribers only), direct messages, blocking, reports, the owner's moderation and live delivery. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIntegrationTest {

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

    @BeforeEach
    void names() {
        when(userClient.summaries(any())).thenAnswer(inv -> ((UserClient.IdsRequest) inv.getArgument(0)).ids().stream()
                .map(id -> new UserClient.UserSummary(id, "User " + id.toString().substring(0, 4), null))
                .toList());
    }

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, id + "@example.com", "User " + id.toString().substring(0, 4),
                List.of(roles));
    }

    private String user(UUID id) {
        return token(id, "USER");
    }

    private String owner(UUID id) {
        return token(id, "USER", "CLUB_OWNER");
    }

    private String createClub(UUID ownerId) throws Exception {
        // owners get their clubs from approved applications; here an admin creates it for them
        String body = mvc.perform(post("/api/v1/clubs").header("Authorization", token(UUID.randomUUID(), "USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chat %s\",\"city\":\"Chennai\",\"clubType\":\"RUNNING\",\"ownerId\":\"%s\"}"
                                .formatted(UUID.randomUUID().toString().substring(0, 8), ownerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    private void subscribe(String clubId, UUID userId) throws Exception {
        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", user(userId)))
                .andExpect(status().isCreated());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String openClubChat(String clubId, String auth) throws Exception {
        return read(mvc.perform(get("/api/v1/chat/clubs/{id}", clubId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("GROUP"))
                .andReturn()).get("id").asString();
    }

    private String send(String conversationId, String auth, String text) throws Exception {
        return read(mvc.perform(post("/api/v1/chat/conversations/{id}/messages", conversationId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"%s\"}".formatted(text)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    private JsonNode inboxEntry(String auth, String conversationId) throws Exception {
        JsonNode inbox = read(mvc.perform(get("/api/v1/chat/inbox").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn());
        return inbox.valueStream().filter(c -> conversationId.equals(c.path("id").asString(null))).findFirst().orElseThrow();
    }

    @Test
    void theClubChatIsForSubscribersAndTheOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID follower = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, member);
        mvc.perform(post("/api/v1/clubs/{id}/follow", clubId).header("Authorization", user(follower)));

        mvc.perform(get("/api/v1/chat/clubs/{id}", clubId).header("Authorization", user(follower)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIBERS_ONLY"));
        // before anyone opens it, the inbox lists the club chat without an ID
        JsonNode inbox = read(mvc.perform(get("/api/v1/chat/inbox").header("Authorization", user(member))).andReturn());
        assertThat(inbox.valueStream().anyMatch(c -> clubId.equals(c.path("clubId").asString(null)) && c.get("id").isNull()))
                .isTrue();

        String chat = openClubChat(clubId, user(member));
        assertThat(openClubChat(clubId, owner(ownerId))).isEqualTo(chat);
        send(chat, user(member), "Who is running tomorrow?");
        send(chat, user(member), "6 am at the lighthouse");

        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(follower)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", owner(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].body").value("Who is running tomorrow?"))
                .andExpect(jsonPath("$[1].senderName").value("User " + member.toString().substring(0, 4)));
        // paging back from the newest
        String newest = read(mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat)
                .header("Authorization", owner(ownerId))).andReturn()).get(1).get("id").asString();
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat).param("before", newest)
                        .header("Authorization", owner(ownerId)))
                .andExpect(jsonPath("$", hasSize(1)));

        JsonNode entry = inboxEntry(owner(ownerId), chat);
        assertThat(entry.get("unread").asLong()).isEqualTo(2);
        assertThat(entry.get("lastMessage").get("body").asString()).isEqualTo("6 am at the lighthouse");
        assertThat(entry.get("canModerate").asBoolean()).isTrue();
        mvc.perform(post("/api/v1/chat/conversations/{id}/read", chat).header("Authorization", owner(ownerId)))
                .andExpect(status().isNoContent());
        assertThat(inboxEntry(owner(ownerId), chat).get("unread").asLong()).isZero();
        assertThat(inboxEntry(user(member), chat).get("unread").asLong()).isZero();

        // leaving the club ends access
        mvc.perform(delete("/api/v1/clubs/{id}/members/me", clubId).header("Authorization", user(member)));
        mvc.perform(post("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"bye\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theOwnerDeletesMessagesAndMutesPeople() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, member);
        subscribe(clubId, other);
        String chat = openClubChat(clubId, user(member));
        String spam = send(chat, user(member), "Buy my shoes");

        mvc.perform(delete("/api/v1/chat/messages/{id}", spam).header("Authorization", user(other)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/chat/messages/{id}", spam).header("Authorization", owner(ownerId)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(other)))
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].body").doesNotExist());

        String mutes = "/api/v1/chat/clubs/" + clubId + "/mutes/";
        mvc.perform(put(mutes + member).header("Authorization", user(other))).andExpect(status().isForbidden());
        mvc.perform(put(mutes + ownerId).header("Authorization", owner(ownerId))).andExpect(status().isBadRequest());
        mvc.perform(put(mutes + member).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hours\":24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mutedUntil").exists());
        mvc.perform(post("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hello?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_MUTED"));
        mvc.perform(get("/api/v1/chat/conversations/{id}", chat).header("Authorization", user(member)))
                .andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.reason").value("MUTED"));
        mvc.perform(get("/api/v1/chat/clubs/{id}/mutes", clubId).header("Authorization", owner(ownerId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(member.toString()));

        mvc.perform(delete(mutes + member).header("Authorization", owner(ownerId))).andExpect(status().isNoContent());
        send(chat, user(member), "Sorry everyone");
    }

    @Test
    void directMessagesAreBetweenPeopleWhoShareAClub() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID asha = UUID.randomUUID();
        UUID ravi = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, asha);
        subscribe(clubId, ravi);

        mvc.perform(post("/api/v1/chat/direct").header("Authorization", user(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(asha)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_CONNECTED"));
        mvc.perform(post("/api/v1/chat/direct").header("Authorization", user(asha))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(asha)))
                .andExpect(status().isBadRequest());

        String dm = read(mvc.perform(post("/api/v1/chat/direct").header("Authorization", user(asha))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(ravi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("DIRECT"))
                .andExpect(jsonPath("$.otherUserId").value(ravi.toString()))
                .andExpect(jsonPath("$.title").value("User " + ravi.toString().substring(0, 4)))
                .andExpect(jsonPath("$.canSend").value(true))
                .andReturn()).get("id").asString();
        // the same pair always gets the same conversation, whoever starts it
        String again = read(mvc.perform(post("/api/v1/chat/direct").header("Authorization", user(ravi))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(asha))).andReturn())
                .get("id").asString();
        assertThat(again).isEqualTo(dm);

        send(dm, user(asha), "Want to pace me on Sunday?");
        send(dm, user(asha), "Around 5:30/km");
        // one notification for the first unread message, not one per message
        verify(notificationClient, times(1)).create(argThat(n -> n.userId().equals(ravi) && n.type().equals("CHAT_MESSAGE")));
        assertThat(inboxEntry(user(ravi), dm).get("unread").asLong()).isEqualTo(2);

        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", dm).header("Authorization", user(outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", dm).header("Authorization", token(UUID.randomUUID(), "USER", "ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", dm).header("Authorization", user(ravi)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void blockingStopsDirectMessagesAndHidesGroupMessages() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID asha = UUID.randomUUID();
        UUID ravi = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, asha);
        subscribe(clubId, ravi);
        String dm = read(mvc.perform(post("/api/v1/chat/direct").header("Authorization", user(asha))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(ravi))).andReturn())
                .get("id").asString();
        String chat = openClubChat(clubId, user(asha));
        send(chat, user(asha), "Hi all");

        mvc.perform(put("/api/v1/chat/blocks/{id}", asha).header("Authorization", user(ravi))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/chat/blocks").header("Authorization", user(ravi)))
                .andExpect(jsonPath("$[0].userId").value(asha.toString()));
        mvc.perform(post("/api/v1/chat/conversations/{id}/messages", dm).header("Authorization", user(asha))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hey\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_BLOCKED"));
        mvc.perform(get("/api/v1/chat/conversations/{id}", dm).header("Authorization", user(ravi)))
                .andExpect(jsonPath("$.reason").value("BLOCKED"));
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(ravi)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", owner(ownerId)))
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(delete("/api/v1/chat/blocks/{id}", asha).header("Authorization", user(ravi))).andExpect(status().isNoContent());
        send(dm, user(asha), "hey");
    }

    @Test
    void reportedMessagesWaitForAnAdmin() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID asha = UUID.randomUUID();
        UUID ravi = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, asha);
        subscribe(clubId, ravi);
        String chat = openClubChat(clubId, user(asha));
        String rude = send(chat, user(asha), "Something rude");

        mvc.perform(post("/api/v1/chat/messages/{id}/report", rude).header("Authorization", user(asha)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chat/messages/{id}/report", rude).header("Authorization", user(ravi))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Abusive\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/chat/messages/{id}/report", rude).header("Authorization", user(ravi)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/chat/reports").header("Authorization", user(ravi))).andExpect(status().isForbidden());

        JsonNode reports = read(mvc.perform(get("/api/v1/chat/reports").header("Authorization", token(admin, "USER", "ADMIN")))
                .andExpect(status().isOk()).andReturn());
        JsonNode report = reports.get("items").valueStream()
                .filter(r -> rude.equals(r.get("message").get("id").asString())).findFirst().orElseThrow();
        assertThat(report.get("reason").asString()).isEqualTo("Abusive");
        assertThat(report.get("message").get("body").asString()).isEqualTo("Something rude");
        assertThat(report.get("clubId").asString()).isEqualTo(clubId);

        mvc.perform(post("/api/v1/chat/reports/{id}/resolve", report.get("id").asString())
                        .header("Authorization", token(admin, "USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"removeMessage\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(ravi)))
                .andExpect(jsonPath("$[0].deleted").value(true));
        mvc.perform(get("/api/v1/chat/reports").param("status", "REMOVED").header("Authorization", token(admin, "USER", "ADMIN")))
                .andExpect(jsonPath("$.items[?(@.message.id == '%s')]".formatted(rude)).exists());
    }

    @Test
    void photosMustBeUploadedByTheSender() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, member);
        String chat = openClubChat(clubId, user(member));
        String url = "/media/chat/" + member + "/pic.jpg";
        when(media.storeImage(eq("chat/" + member), any())).thenReturn(new StoredImage(url, "chat/" + member + "/pic.jpg"));

        mvc.perform(multipart("/api/v1/chat/photos").file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1}))
                        .header("Authorization", user(member)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(url));
        mvc.perform(post("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"photoUrl\":\"%s\"}".formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photoUrl").value(url));
        mvc.perform(post("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"photoUrl\":\"%s\"}".formatted(url)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void connectedMembersGetNewMessagesLive() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, member);
        String chat = openClubChat(clubId, user(member));

        MvcResult memberStream = mvc.perform(get("/api/v1/chat/stream").header("Authorization", user(member)))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult outsiderStream = mvc.perform(get("/api/v1/chat/stream").header("Authorization", user(outsider)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(get("/api/v1/chat/stream")).andExpect(status().isUnauthorized());

        String id = send(chat, owner(ownerId), "Rain check: we start at 7 tomorrow");

        String received = memberStream.getResponse().getContentAsString();
        assertThat(received).contains("event:ready");
        assertThat(received).contains("event:message").contains("id:" + id).contains("Rain check: we start at 7 tomorrow");
        assertThat(outsiderStream.getResponse().getContentAsString()).doesNotContain("Rain check");
        assertThat(memberStream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");

        mvc.perform(delete("/api/v1/chat/messages/{id}", id).header("Authorization", owner(ownerId)));
        assertThat(memberStream.getResponse().getContentAsString()).contains("\"deleted\":true");
    }
}

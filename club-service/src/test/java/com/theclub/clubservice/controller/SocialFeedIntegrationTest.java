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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Subscribers posting, likes, comments, reshares and the public preview of a shared link. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialFeedIntegrationTest {

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
                .map(id -> new UserClient.UserSummary(id, "User " + id.toString().substring(0, 4), "/media/profiles/" + id + ".png"))
                .toList());
    }

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, id + "@example.com", "User " + id.toString().substring(0, 4),
                List.of(roles));
    }

    private String owner(UUID id) {
        return token(id, "USER", "CLUB_OWNER");
    }

    private String user(UUID id) {
        return token(id, "USER");
    }

    private String createClub(UUID ownerId) throws Exception {
        String body = mvc.perform(post("/api/v1/clubs").header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Social %s\",\"city\":\"Chennai\",\"clubType\":\"RUNNING\"}"
                                .formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    private void subscribe(String clubId, UUID userId) throws Exception {
        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", user(userId)))
                .andExpect(status().isCreated());
    }

    private JsonNode createPost(String clubId, String auth, String body) throws Exception {
        String response = mvc.perform(post("/api/v1/clubs/{id}/posts", clubId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private static String simplePost(String title) {
        return "{\"title\":\"%s\",\"content\":\"Hello club\"}".formatted(title);
    }

    @Test
    void subscribersPostWithUploadedPhotosAndTheOwnerHears() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, member);

        String url = "/media/posts/" + clubId + "/photo.png";
        when(media.storeImage(eq("posts/" + clubId), any())).thenReturn(new StoredImage(url, "posts/" + clubId + "/photo.png"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        mvc.perform(multipart("/api/v1/clubs/{id}/posts/photos", clubId).file(file).header("Authorization", user(member)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(url));
        // someone outside the club can't upload or post
        UUID stranger = UUID.randomUUID();
        mvc.perform(multipart("/api/v1/clubs/{id}/posts/photos", clubId).file(file).header("Authorization", user(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clubs/{id}/posts", clubId).header("Authorization", user(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(simplePost("Hi")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIBERS_ONLY"));

        JsonNode post = createPost(clubId, user(member),
                "{\"title\":\"My first 10k\",\"content\":\"Thanks everyone\",\"photoUrls\":[\"%s\"]}".formatted(url));
        org.assertj.core.api.Assertions.assertThat(post.get("type").asString()).isEqualTo("POST");
        org.assertj.core.api.Assertions.assertThat(post.get("authorName").asString()).startsWith("User ");
        org.assertj.core.api.Assertions.assertThat(post.get("photoUrls").get(0).asString()).isEqualTo(url);
        verify(notificationClient).create(argThat(n -> n.userId().equals(ownerId) && n.type().equals("CLUB_MEMBER_POST")));

        // only the owner writes announcements and recaps; uploads belong to their club
        mvc.perform(post("/api/v1/clubs/{id}/posts", clubId).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ANNOUNCEMENT\",\"title\":\"Hi\",\"content\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clubs/{id}/posts", clubId).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hi\",\"content\":\"x\",\"photoUrls\":[\"/media/posts/%s/x.png\"]}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorsEditTheirPostsAndTheOwnerCanRemoveAny() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, author);
        subscribe(clubId, other);
        String first = createPost(clubId, user(author), simplePost("Morning run")).get("id").asString();
        String second = createPost(clubId, user(author), simplePost("Evening ride")).get("id").asString();

        mvc.perform(put("/api/v1/clubs/{c}/posts/{p}", clubId, first).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Edited by owner\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/clubs/{c}/posts/{p}", clubId, first).header("Authorization", user(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Morning 10k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Morning 10k"));

        mvc.perform(delete("/api/v1/clubs/{c}/posts/{p}", clubId, first).header("Authorization", user(other)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/clubs/{c}/posts/{p}", clubId, first).header("Authorization", owner(ownerId)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/clubs/{c}/posts/{p}", clubId, second).header("Authorization", user(author)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/clubs/{c}/posts", clubId).header("Authorization", user(other)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void likesAreOnePerPersonAndShowInTheFeed() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID fan = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String clubId = createClub(ownerId);
        String postId = createPost(clubId, owner(ownerId), simplePost("Race day")).get("id").asString();

        mvc.perform(put("/api/v1/clubs/{c}/posts/{p}/like", clubId, postId).header("Authorization", user(fan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(put("/api/v1/clubs/{c}/posts/{p}/like", clubId, postId).header("Authorization", user(fan)))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(put("/api/v1/clubs/{c}/posts/{p}/like", clubId, postId).header("Authorization", user(other)))
                .andExpect(jsonPath("$.likeCount").value(2));

        mvc.perform(get("/api/v1/clubs/{c}/posts", clubId).header("Authorization", user(fan)))
                .andExpect(jsonPath("$.items[0].likeCount").value(2))
                .andExpect(jsonPath("$.items[0].likedByMe").value(true));
        mvc.perform(delete("/api/v1/clubs/{c}/posts/{p}/like", clubId, postId).header("Authorization", user(fan)))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(get("/api/v1/clubs/{c}/posts/{p}", clubId, postId).header("Authorization", user(fan)))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    @Test
    void followersAndSubscribersCommentAndTheAuthorIsTold() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID follower = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String clubId = createClub(ownerId);
        subscribe(clubId, member);
        mvc.perform(post("/api/v1/clubs/{id}/follow", clubId).header("Authorization", user(follower)));
        String postId = createPost(clubId, owner(ownerId), simplePost("Route for Sunday")).get("id").asString();
        String comments = "/api/v1/clubs/" + clubId + "/posts/" + postId + "/comments";

        mvc.perform(post(comments).header("Authorization", user(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Nice\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUDIENCE_ONLY"));
        String followerComment = json.readTree(mvc.perform(post(comments).header("Authorization", user(follower))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  Count me in!  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Count me in!"))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        String memberComment = json.readTree(mvc.perform(post(comments).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Me too\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        verify(notificationClient, times(2)).create(argThat(n -> n.userId().equals(ownerId) && n.type().equals("POST_COMMENT")));

        mvc.perform(get(comments).header("Authorization", user(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].content").value("Count me in!"))
                .andExpect(jsonPath("$.items[0].authorName").value("User " + follower.toString().substring(0, 4)));
        mvc.perform(get("/api/v1/clubs/{c}/posts", clubId).header("Authorization", user(member)))
                .andExpect(jsonPath("$.items[0].commentCount").value(2));

        mvc.perform(delete(comments + "/" + followerComment).header("Authorization", user(member)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(comments + "/" + followerComment).header("Authorization", user(follower)))
                .andExpect(status().isNoContent());
        mvc.perform(delete(comments + "/" + memberComment).header("Authorization", owner(ownerId)))
                .andExpect(status().isNoContent());
        mvc.perform(get(comments).header("Authorization", user(member)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void ownersReshareOtherClubsPostsAndEvents() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID ownerC = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        String clubA = createClub(ownerA);
        String clubB = createClub(ownerB);
        String clubC = createClub(ownerC);
        subscribe(clubB, member);
        String original = createPost(clubA, owner(ownerA),
                "{\"title\":\"Charity run\",\"content\":\"Join us\",\"photoUrls\":[\"https://img/run.jpg\"]}")
                .get("id").asString();
        String reshare = "/api/v1/clubs/%s/posts/reshare";

        mvc.perform(post(reshare.formatted(clubB)).header("Authorization", user(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postId\":\"%s\"}".formatted(original)))
                .andExpect(status().isForbidden());
        mvc.perform(post(reshare.formatted(clubA)).header("Authorization", owner(ownerA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postId\":\"%s\"}".formatted(original)))
                .andExpect(status().isBadRequest());
        String shared = json.readTree(mvc.perform(post(reshare.formatted(clubB)).header("Authorization", owner(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"%s\",\"comment\":\"Worth it\"}".formatted(original)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RESHARE"))
                .andExpect(jsonPath("$.content").value("Worth it"))
                .andExpect(jsonPath("$.shared.kind").value("POST"))
                .andExpect(jsonPath("$.shared.available").value(true))
                .andExpect(jsonPath("$.shared.clubId").value(clubA))
                .andExpect(jsonPath("$.shared.title").value("Charity run"))
                .andExpect(jsonPath("$.shared.photoUrl").value("https://img/run.jpg"))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        verify(notificationClient).create(argThat(n -> n.userId().equals(ownerA) && n.type().equals("POST_RESHARED")));
        verify(notificationClient).create(argThat(n -> n.userId().equals(member) && n.type().equals("CLUB_POST")));
        mvc.perform(post(reshare.formatted(clubB)).header("Authorization", owner(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postId\":\"%s\"}".formatted(original)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESHARED"));

        // resharing the reshare shares the original
        mvc.perform(post(reshare.formatted(clubC)).header("Authorization", owner(ownerC))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postId\":\"%s\"}".formatted(shared)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shared.postId").value(original));

        UUID eventId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 12, 6, 6, 0);
        when(eventClient.get(eventId)).thenReturn(new EventClient.EventSummary(eventId, UUID.fromString(clubA),
                "Marina 10k", start, "SCHEDULED"));
        mvc.perform(post(reshare.formatted(clubB)).header("Authorization", owner(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"%s\"}".formatted(eventId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shared.kind").value("EVENT"))
                .andExpect(jsonPath("$.shared.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.shared.title").value("Marina 10k"))
                .andExpect(jsonPath("$.shared.clubId").value(clubA));
        UUID cancelled = UUID.randomUUID();
        when(eventClient.get(cancelled)).thenReturn(new EventClient.EventSummary(cancelled, UUID.fromString(clubA),
                "Called off", start, "CANCELLED"));
        mvc.perform(post(reshare.formatted(clubB)).header("Authorization", owner(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"%s\"}".formatted(cancelled)))
                .andExpect(status().isBadRequest());

        // once the original is deleted the reshare says so
        mvc.perform(delete("/api/v1/clubs/{c}/posts/{p}", clubA, original).header("Authorization", owner(ownerA)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/clubs/{c}/posts/{p}", clubB, shared).header("Authorization", user(member)))
                .andExpect(jsonPath("$.shared.available").value(false));
    }

    @Test
    void sharedLinksShowAPreviewWithoutSigningIn() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String clubId = createClub(ownerId);
        String postId = createPost(clubId, owner(ownerId),
                "{\"title\":\"Open day\",\"content\":\"%s\",\"photoUrls\":[\"https://img/open.jpg\"]}"
                        .formatted("Come and try a run with us. ".repeat(20).strip())).get("id").asString();

        mvc.perform(get("/api/v1/clubs/{c}/posts/{p}/preview", clubId, postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Open day"))
                .andExpect(jsonPath("$.clubName").value(org.hamcrest.Matchers.startsWith("Social ")))
                .andExpect(jsonPath("$.photoUrl").value("https://img/open.jpg"))
                .andExpect(jsonPath("$.excerpt").value(org.hamcrest.Matchers.endsWith("…")))
                .andExpect(jsonPath("$.authorName").value("User " + ownerId.toString().substring(0, 4)));
        // the full post, its comments and the feed still need an account
        mvc.perform(get("/api/v1/clubs/{c}/posts/{p}", clubId, postId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/clubs/{c}/posts/{p}/comments", clubId, postId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/clubs/{c}/posts/{p}/preview", clubId, UUID.randomUUID())).andExpect(status().isNotFound());
        verify(notificationClient, never()).create(argThat(n -> n.type().equals("POST_COMMENT")));
    }
}

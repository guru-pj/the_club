package com.theclub.clubservice.controller;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.client.NotificationClient;
import com.theclub.clubservice.client.UserClient;
import com.theclub.common.media.MediaStore;
import com.theclub.common.media.StoredImage;
import com.theclub.common.security.JwtService;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User journey steps 3–5: discovery, club details (photos, reviews), following and subscribing. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClubJourneyIntegrationTest {

    // Chennai Marina, Besant Nagar (~7 km), Kanchipuram (~70 km)
    static final double MARINA_LAT = 13.0500, MARINA_LNG = 80.2824;
    static final double BESANT_LAT = 12.9990, BESANT_LNG = 80.2707;
    static final double KANCHI_LAT = 12.8342, KANCHI_LNG = 79.7036;

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

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, id + "@example.com", "User " + id.toString().substring(0, 4),
                List.of(roles));
    }

    private String owner(UUID id) {
        return token(id, "USER", "CLUB_OWNER");
    }

    /** Club names are unique per test: every test class shares one H2 database. */
    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String createClub(UUID ownerId, String name, Double lat, Double lng, String extra) throws Exception {
        String location = lat == null ? "" : ",\"latitude\":%s,\"longitude\":%s".formatted(lat, lng);
        String body = mvc.perform(post("/api/v1/clubs").header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"city\":\"Chennai\",\"clubType\":\"RUNNING\"%s%s}"
                                .formatted(name, location, extra)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    @Test
    void nearbyListsClubsWithinTheRadiusClosestFirst() throws Exception {
        String marina = createClub(UUID.randomUUID(), unique("Marina Runners"), MARINA_LAT, MARINA_LNG, "");
        String besant = createClub(UUID.randomUUID(), unique("Besant Riders"), BESANT_LAT, BESANT_LNG, "");
        String kanchi = createClub(UUID.randomUUID(), unique("Kanchi Cyclists"), KANCHI_LAT, KANCHI_LNG, "");
        String noLocation = createClub(UUID.randomUUID(), unique("Somewhere Gym"), null, null, "");
        String me = token(UUID.randomUUID(), "USER");

        String body = mvc.perform(get("/api/v1/clubs/nearby").param("lat", "13.0510").param("lng", "80.2830")
                        .param("radiusKm", "25").header("Authorization", me))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = json.readTree(body).valueStream().map(n -> n.get("id").asString()).toList();
        assertOrder(ids, marina, besant);
        org.assertj.core.api.Assertions.assertThat(ids).doesNotContain(kanchi, noLocation);

        JsonNode first = json.readTree(body).valueStream().filter(n -> n.get("id").asString().equals(marina))
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(first.get("distanceKm").asDouble()).isLessThan(0.5);

        mvc.perform(get("/api/v1/clubs/nearby").param("lat", "13.0510").param("lng", "80.2830")
                        .param("radiusKm", "100").header("Authorization", me))
                .andExpect(jsonPath("$[*].id", hasItem(kanchi)));
        mvc.perform(get("/api/v1/clubs/nearby").param("lat", "95").param("lng", "80").header("Authorization", me))
                .andExpect(status().isBadRequest());
    }

    private static void assertOrder(List<String> ids, String closer, String farther) {
        org.assertj.core.api.Assertions.assertThat(ids).contains(closer, farther);
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(closer)).isLessThan(ids.indexOf(farther));
    }

    @Test
    void followingIsSeparateFromSubscribing() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String clubId = createClub(ownerId, unique("Follow Club"), null, null, "");
        UUID fan = UUID.randomUUID();
        String fanToken = token(fan, "USER");

        mvc.perform(post("/api/v1/clubs/{id}/follow", clubId).header("Authorization", fanToken))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.following").value(true));
        mvc.perform(post("/api/v1/clubs/{id}/follow", clubId).header("Authorization", fanToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", fanToken))
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.myRole").doesNotExist());
        mvc.perform(get("/api/v1/clubs/me/following").header("Authorization", fanToken))
                .andExpect(jsonPath("$[*].id", hasItem(clubId)));

        // Followers hear about posts
        mvc.perform(post("/api/v1/clubs/{id}/posts", clubId).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Saturday 6am\",\"content\":\"Meet at the lighthouse\"}"))
                .andExpect(status().isCreated());
        verify(notificationClient).create(argThat(n -> n.userId().equals(fan) && n.type().equals("CLUB_POST")));

        mvc.perform(delete("/api/v1/clubs/{id}/follow", clubId).header("Authorization", fanToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", fanToken))
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    void clubsWithoutSubscriptionsCanOnlyBeFollowed() throws Exception {
        String clubId = createClub(UUID.randomUUID(), unique("Open Track"), null, null,
                ",\"subscriptionsEnabled\":false");
        String me = token(UUID.randomUUID(), "USER");

        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", me))
                .andExpect(jsonPath("$.subscriptionsEnabled").value(false));
        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", me))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTIONS_DISABLED"));
        mvc.perform(post("/api/v1/clubs/{id}/follow", clubId).header("Authorization", me))
                .andExpect(status().isCreated());
    }

    @Test
    void subscriptionBenefitsAreShownAndEditable() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String clubId = createClub(ownerId, unique("Benefit Club"), null, null,
                ",\"subscriptionBenefits\":\"Members-only long runs\"");
        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", token(UUID.randomUUID(), "USER")))
                .andExpect(jsonPath("$.subscriptionsEnabled").value(true))
                .andExpect(jsonPath("$.subscriptionBenefits").value("Members-only long runs"));
        mvc.perform(put("/api/v1/clubs/{id}", clubId).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscriptionBenefits\":\"\",\"latitude\":13.05,\"longitude\":80.28,\"address\":\"Marina Beach\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionBenefits").doesNotExist())
                .andExpect(jsonPath("$.address").value("Marina Beach"))
                .andExpect(jsonPath("$.latitude").value(13.05));
        mvc.perform(put("/api/v1/clubs/{id}", clubId).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":13.05}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPLETE_LOCATION"));
    }

    @Test
    void onlySubscribersAndAttendeesReviewAndTheOwnerReplies() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String clubId = createClub(ownerId, unique("Review Club"), null, null, "");
        UUID member = UUID.randomUUID();
        UUID attendee = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        mvc.perform(post("/api/v1/clubs/{id}/join", clubId).header("Authorization", token(member, "USER")))
                .andExpect(status().isCreated());
        when(eventClient.attendance(eq(UUID.fromString(clubId)), any()))
                .thenAnswer(inv -> new EventClient.AttendanceCheck(attendee.equals(inv.getArgument(1))));
        when(userClient.summaries(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/clubs/{id}/reviews", clubId).header("Authorization", token(stranger, "USER")))
                .andExpect(jsonPath("$.canReview").value(false))
                .andExpect(jsonPath("$.summary.count").value(0));
        mvc.perform(put("/api/v1/clubs/{id}/reviews/me", clubId).header("Authorization", token(stranger, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE_TO_REVIEW"));
        mvc.perform(put("/api/v1/clubs/{id}/reviews/me", clubId).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OWN_CLUB"));

        mvc.perform(put("/api/v1/clubs/{id}/reviews/me", clubId).header("Authorization", token(member, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Great pacers\"}"))
                .andExpect(status().isOk());
        String review = mvc.perform(put("/api/v1/clubs/{id}/reviews/me", clubId).header("Authorization", token(attendee, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Editing replaces the review rather than adding a second one
        mvc.perform(put("/api/v1/clubs/{id}/reviews/me", clubId).header("Authorization", token(attendee, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":2,\"comment\":\"Too fast for me\"}"))
                .andExpect(jsonPath("$.rating").value(2));
        String reviewId = json.readTree(review).get("id").asString();

        mvc.perform(get("/api/v1/clubs/{id}/reviews", clubId).header("Authorization", token(attendee, "USER")))
                .andExpect(jsonPath("$.summary.count").value(2))
                .andExpect(jsonPath("$.summary.average").value(closeTo(3.5, 0.001)))
                .andExpect(jsonPath("$.summary.byRating.2").value(1))
                .andExpect(jsonPath("$.canReview").value(true))
                .andExpect(jsonPath("$.mine.comment").value("Too fast for me"));
        mvc.perform(get("/api/v1/clubs/{id}", clubId).header("Authorization", token(stranger, "USER")))
                .andExpect(jsonPath("$.ratingAverage").value(closeTo(3.5, 0.001)))
                .andExpect(jsonPath("$.ratingCount").value(2));

        mvc.perform(put("/api/v1/clubs/{id}/reviews/{r}/reply", clubId, reviewId).header("Authorization", owner(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"Try the 7am group!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerReply").value("Try the 7am group!"));
        mvc.perform(put("/api/v1/clubs/{id}/reviews/{r}/reply", clubId, reviewId).header("Authorization", token(member, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"x\"}"))
                .andExpect(status().isForbidden());
        // Owners can't delete reviews; admins moderate
        mvc.perform(delete("/api/v1/clubs/{id}/reviews/{r}", clubId, reviewId).header("Authorization", owner(ownerId)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/clubs/{id}/reviews/{r}", clubId, reviewId)
                        .header("Authorization", token(UUID.randomUUID(), "USER", "ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/clubs/{id}/reviews", clubId).header("Authorization", token(stranger, "USER")))
                .andExpect(jsonPath("$.summary.count").value(1));
    }

    @Test
    void ownersUploadPhotosAndALogo() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String clubId = createClub(ownerId, unique("Photo Club"), null, null, "");
        when(media.storeImage(eq("clubs/" + clubId), any()))
                .thenReturn(new StoredImage("/media/clubs/" + clubId + "/a.jpg", "k1"))
                .thenReturn(new StoredImage("/media/clubs/" + clubId + "/logo.png", "k2"));
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});

        String photo = mvc.perform(multipart("/api/v1/clubs/{id}/photos", clubId).file(file).param("caption", "Finish line")
                        .header("Authorization", owner(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caption").value("Finish line"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(multipart("/api/v1/clubs/{id}/photos", clubId).file(file).header("Authorization", token(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/clubs/{id}/photos", clubId).header("Authorization", token(UUID.randomUUID(), "USER")))
                .andExpect(jsonPath("$[0].url").value("/media/clubs/" + clubId + "/a.jpg"));

        mvc.perform(multipart("/api/v1/clubs/{id}/logo", clubId).file(file).header("Authorization", owner(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value("/media/clubs/" + clubId + "/logo.png"));

        String photoId = json.readTree(photo).get("id").asString();
        mvc.perform(delete("/api/v1/clubs/{id}/photos/{p}", clubId, photoId).header("Authorization", owner(ownerId)))
                .andExpect(status().isNoContent());
        verify(media).deleteByUrl("/media/clubs/" + clubId + "/a.jpg");
        mvc.perform(get("/api/v1/clubs/{id}/photos", clubId).header("Authorization", owner(ownerId)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void popularPutsTheMostFollowedAndSubscribedFirst() throws Exception {
        String quiet = createClub(UUID.randomUUID(), unique("Quiet Club"), null, null, "");
        String busy = createClub(UUID.randomUUID(), unique("Busy Club"), null, null, "");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/clubs/{id}/follow", busy).header("Authorization", token(UUID.randomUUID(), "USER")));
        }
        mvc.perform(post("/api/v1/clubs/{id}/join", busy).header("Authorization", token(UUID.randomUUID(), "USER")));

        String body = mvc.perform(get("/api/v1/clubs/popular").param("limit", "50")
                        .header("Authorization", token(UUID.randomUUID(), "USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertOrder(json.readTree(body).valueStream().map(n -> n.get("id").asString()).toList(), busy, quiet);
    }

    @Test
    void internalEndpointsServeTheEventFeed() throws Exception {
        UUID user = UUID.randomUUID();
        String followed = createClub(UUID.randomUUID(), unique("Feed Followed"), MARINA_LAT, MARINA_LNG, "");
        String subscribed = createClub(UUID.randomUUID(), unique("Feed Subscribed"), null, null, "");
        mvc.perform(post("/api/v1/clubs/{id}/follow", followed).header("Authorization", token(user, "USER")));
        mvc.perform(post("/api/v1/clubs/{id}/join", subscribed).header("Authorization", token(user, "USER")));

        mvc.perform(get("/internal/clubs/users/{u}/related", user).header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followed", containsInAnyOrder(followed)))
                .andExpect(jsonPath("$.subscribed", containsInAnyOrder(subscribed)));
        mvc.perform(get("/internal/clubs/{c}/audience", subscribed).header("X-Internal-Key", "test-internal-key"))
                .andExpect(jsonPath("$", hasItem(user.toString())));
        mvc.perform(get("/internal/clubs/nearby").param("lat", String.valueOf(MARINA_LAT)).param("lng", String.valueOf(MARINA_LNG))
                        .header("X-Internal-Key", "test-internal-key"))
                .andExpect(jsonPath("$[*].id", hasItem(followed)))
                .andExpect(jsonPath("$[*].id", not(hasItem(subscribed))));
        // Internal endpoints need the key
        mvc.perform(get("/internal/clubs/users/{u}/related", user).header("Authorization", token(user, "USER")))
                .andExpect(status().isForbidden());
    }
}

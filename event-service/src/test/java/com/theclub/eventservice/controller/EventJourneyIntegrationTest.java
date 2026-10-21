package com.theclub.eventservice.controller;

import com.theclub.common.media.MediaStore;
import com.theclub.common.media.StoredImage;
import com.theclub.common.security.JwtService;
import com.theclub.eventservice.MutableClock;
import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.ClubClient.ClubInfo;
import com.theclub.eventservice.client.ClubClient.ClubSummary;
import com.theclub.eventservice.client.ClubClient.Membership;
import com.theclub.eventservice.client.ClubClient.NearbyClub;
import com.theclub.eventservice.client.ClubClient.RelatedClubs;
import com.theclub.eventservice.client.NotificationClient;
import com.theclub.eventservice.client.UserClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User journey steps 6–9 on the event side: the upcoming feed, event details, images, announcements, attendance. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EventJourneyIntegrationTest.ClockConfig.class)
class EventJourneyIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant START = Instant.parse("2026-10-10T04:30:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        MutableClock testClock() {
            return new MutableClock(START, ZONE);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;
    @Autowired
    MutableClock clock;

    @MockitoBean
    ClubClient clubClient;
    @MockitoBean
    UserClient userClient;
    @MockitoBean
    NotificationClient notificationClient;
    @MockitoBean
    MediaStore media;

    // One club per relation to the user, each with a fresh id: the H2 database is shared by every test class
    final UUID followedClub = UUID.randomUUID();
    final UUID subscribedClub = UUID.randomUUID();
    final UUID nearbyClub = UUID.randomUUID();
    final UUID unrelatedClub = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock.set(START);
        reset(clubClient, notificationClient, media);
        for (UUID club : List.of(followedClub, subscribedClub, nearbyClub, unrelatedClub)) {
            when(clubClient.getClub(club)).thenReturn(new ClubInfo(club, "Club " + club, ownerId, 1));
        }
        when(clubClient.membership(any(), eq(userId))).thenReturn(new Membership(false, null));
        when(notificationClient.create(any())).thenReturn(new NotificationClient.NotificationCreated(UUID.randomUUID()));
    }

    /** Works for plain and multipart requests (separate builder types since Spring Framework 7). */
    private <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, UUID id, String... roles) {
        return request.header("Authorization", "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles)));
    }

    private String createEvent(UUID clubId, String title, LocalDateTime start, String extra) throws Exception {
        String body = mvc.perform(as(post("/api/v1/events"), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clubId\":\"%s\",\"title\":\"%s\",\"location\":\"Marina\",\"date\":\"%s\"%s}"
                                .formatted(clubId, title, start, extra)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    @Test
    void upcomingFeedCombinesFollowedSubscribedAndNearbyClubsSoonestFirst() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        String later = createEvent(followedClub, "Followed long run", now.plusDays(5), ",\"price\":199");
        String sooner = createEvent(subscribedClub, "Subscribed tempo run", now.plusDays(1), "");
        String near = createEvent(nearbyClub, "Nearby track night", now.plusDays(3), "");
        createEvent(unrelatedClub, "Somewhere else", now.plusDays(2), "");
        String past = createEvent(followedClub, "Already started", now.plusHours(1), "");
        clock.set(START.plusSeconds(2 * 3600));

        when(clubClient.related(userId)).thenReturn(new RelatedClubs(List.of(followedClub), List.of(subscribedClub)));
        when(clubClient.nearby(eq(13.05), eq(80.28), anyDouble(), anyInt()))
                .thenReturn(List.of(new NearbyClub(nearbyClub, "Track Club", "/media/clubs/t/logo.png", 2.4),
                        new NearbyClub(followedClub, "Marina Runners", null, 0.8)));
        when(clubClient.summaries(any())).thenReturn(List.of(new ClubSummary(subscribedClub, "Tempo Club", null)));

        String body = mvc.perform(as(get("/api/v1/events/upcoming").param("lat", "13.05").param("lng", "80.28"), userId, "USER"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> cards = json.readTree(body).valueStream().toList();
        assertThat(cards).extracting(c -> c.get("event").get("id").asString()).containsExactly(sooner, near, later);
        assertThat(cards).extracting(c -> c.get("event").get("id").asString()).doesNotContain(past);

        JsonNode laterCard = cards.get(2);
        assertThat(laterCard.get("clubName").asString()).isEqualTo("Marina Runners");
        assertThat(laterCard.get("sources").valueStream().map(JsonNode::asString)).containsExactlyInAnyOrder("FOLLOWING", "NEARBY");
        assertThat(laterCard.get("distanceKm").asDouble()).isEqualTo(0.8);
        assertThat(laterCard.get("event").get("price").asDouble()).isEqualTo(199.0);
        assertThat(laterCard.get("event").get("registrationState").asString()).isEqualTo("OPEN");
        assertThat(cards.get(0).get("clubName").asString()).isEqualTo("Tempo Club");
        assertThat(cards.get(0).get("sources").valueStream().map(JsonNode::asString)).containsExactly("SUBSCRIBED");
        assertThat(cards.get(1).get("clubLogoUrl").asString()).isEqualTo("/media/clubs/t/logo.png");

        // Without a location: only followed and subscribed clubs
        mvc.perform(as(get("/api/v1/events/upcoming"), userId, "USER"))
                .andExpect(jsonPath("$[*].event.id").value(org.hamcrest.Matchers.containsInAnyOrder(sooner, later)));
    }

    @Test
    void registrationStateExplainsWhyRegistrationIsUnavailable() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        String notYet = createEvent(followedClub, "Opens tomorrow", now.plusDays(4),
                ",\"registrationOpensAt\":\"%s\"".formatted(now.plusDays(1)));
        String full = createEvent(followedClub, "Tiny run", now.plusDays(4), ",\"capacity\":1");
        mvc.perform(as(post("/api/v1/events/{id}/register", full), UUID.randomUUID(), "USER")).andExpect(status().isCreated());

        mvc.perform(as(get("/api/v1/events/{id}", notYet), userId, "USER"))
                .andExpect(jsonPath("$.registrationState").value("NOT_YET_OPEN"));
        mvc.perform(as(get("/api/v1/events/{id}", full), userId, "USER"))
                .andExpect(jsonPath("$.registrationState").value("FULL"))
                .andExpect(jsonPath("$.spotsLeft").value(0));
        mvc.perform(as(post("/api/v1/events/{id}/register", full), userId, "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Event is full"));
    }

    @Test
    void newEventsAreAnnouncedToSubscribersAndFollowersButNotTheAuthor() throws Exception {
        UUID follower = UUID.randomUUID();
        when(clubClient.audience(followedClub)).thenReturn(Set.of(follower, ownerId));

        createEvent(followedClub, "Full moon ride", LocalDateTime.now(clock).plusDays(6), "");

        verify(notificationClient).create(argThat(n -> n.userId().equals(follower)
                && n.type().equals("EVENT_ANNOUNCED") && n.message().contains("Full moon ride")));
        verify(notificationClient, never()).create(argThat(n -> n.userId().equals(ownerId)));
    }

    @Test
    void announcementFailuresDontBlockCreatingTheEvent() throws Exception {
        when(clubClient.audience(followedClub)).thenThrow(new RuntimeException("club-service down"));

        createEvent(followedClub, "Still created", LocalDateTime.now(clock).plusDays(6), "");
    }

    @Test
    void ownersUploadAndRemoveAnEventImage() throws Exception {
        String eventId = createEvent(followedClub, "Photo run", LocalDateTime.now(clock).plusDays(3), "");
        when(media.storeImage(eq("events/" + eventId), any()))
                .thenReturn(new StoredImage("/media/events/" + eventId + "/cover.jpg", "k"));
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1});

        mvc.perform(as(multipart("/api/v1/events/{id}/image", eventId).file(file), userId, "USER"))
                .andExpect(status().isForbidden());
        mvc.perform(as(multipart("/api/v1/events/{id}/image", eventId).file(file), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/media/events/" + eventId + "/cover.jpg"));
        mvc.perform(as(delete("/api/v1/events/{id}/image", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        verify(media).deleteByUrl("/media/events/" + eventId + "/cover.jpg");
    }

    @Test
    void attendanceCountsCheckInsAtAnyEventOfTheClub() throws Exception {
        String eventId = createEvent(subscribedClub, "Check-in run", LocalDateTime.now(clock).plusDays(1), "");
        String register = mvc.perform(as(post("/api/v1/events/{id}/register", eventId), userId, "USER"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String registrationId = json.readTree(register).get("registrationId").asString();

        mvc.perform(get("/internal/events/attendance").param("clubId", subscribedClub.toString())
                        .param("userId", userId.toString()).header("X-Internal-Key", "test-internal-key"))
                .andExpect(jsonPath("$.attended").value(false));

        String ticket = mvc.perform(as(get("/api/v1/events/registrations/{id}/ticket", registrationId), userId, "USER"))
                .andReturn().getResponse().getContentAsString();
        clock.set(START.plusSeconds(24 * 3600));
        mvc.perform(as(post("/api/v1/events/{id}/check-in", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketCode\":\"%s\"}".formatted(json.readTree(ticket).get("ticketCode").asString())))
                .andExpect(status().isOk());

        mvc.perform(get("/internal/events/attendance").param("clubId", subscribedClub.toString())
                        .param("userId", userId.toString()).header("X-Internal-Key", "test-internal-key"))
                .andExpect(jsonPath("$.attended").value(true));
        mvc.perform(get("/internal/events/attendance").param("clubId", followedClub.toString())
                        .param("userId", userId.toString()).header("X-Internal-Key", "test-internal-key"))
                .andExpect(jsonPath("$.attended").value(false));
    }
}

package com.theclub.eventservice.controller;

import com.theclub.common.media.MediaStore;
import com.theclub.common.security.JwtService;
import com.theclub.eventservice.MutableClock;
import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.ClubClient.ClubInfo;
import com.theclub.eventservice.client.ClubClient.Membership;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Club owner journey steps 8–13 on the event side: draft, publish, registrations, check-in, completion, statistics. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EventLifecycleIntegrationTest.ClockConfig.class)
class EventLifecycleIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant START = Instant.parse("2026-10-10T04:30:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        MutableClock lifecycleClock() {
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

    final UUID clubId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final UUID otherOwner = UUID.randomUUID();
    final UUID member = UUID.randomUUID();
    final UUID fan = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock.set(START);
        reset(clubClient, notificationClient);
        when(clubClient.getClub(clubId)).thenReturn(new ClubInfo(clubId, "Marina Runners", ownerId, 1));
        when(clubClient.membership(any(), any())).thenReturn(new Membership(false, null));
        when(clubClient.audience(clubId)).thenReturn(java.util.Set.of(member, fan));
        when(notificationClient.create(any())).thenReturn(new NotificationClient.NotificationCreated(UUID.randomUUID()));
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, UUID id, String... roles) {
        return request.header("Authorization", "Bearer " + jwtService.issueAccessToken(id, "x@example.com", "X", List.of(roles)));
    }

    private String draft(String title, LocalDateTime start) throws Exception {
        String body = mvc.perform(as(post("/api/v1/events"), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clubId\":\"%s\",\"title\":\"%s\",\"location\":\"Marina\",\"date\":\"%s\",\"capacity\":10,\"publish\":false}"
                                .formatted(clubId, title, start)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.registrationState").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    @Test
    void draftsStayWithTheOwnerUntilPublished() throws Exception {
        String eventId = draft("Hill repeats", LocalDateTime.now(clock).plusDays(3));
        verify(notificationClient, never()).create(any());

        // nobody else sees a draft or can register for it
        mvc.perform(as(get("/api/v1/events/{id}", eventId), member, "USER")).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/events/club/{id}", clubId), member, "USER"))
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(eventId)).doesNotExist());
        mvc.perform(as(get("/api/v1/events/club/{id}", clubId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(eventId)).exists());
        mvc.perform(as(post("/api/v1/events/{id}/register", eventId), member, "USER")).andExpect(status().isNotFound());

        // the owner can still edit it, and only the owner publishes it
        mvc.perform(as(put("/api/v1/events/{id}", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hill repeats x8\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/events/{id}/publish", eventId), otherOwner, "USER", "CLUB_OWNER"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/events/{id}/publish", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.registrationState").value("OPEN"));
        verify(notificationClient).create(argThat(n -> n.userId().equals(member) && n.type().equals("EVENT_ANNOUNCED")
                && n.message().contains("Hill repeats x8")));
        mvc.perform(as(post("/api/v1/events/{id}/publish", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));

        mvc.perform(as(get("/api/v1/events/{id}", eventId), member, "USER")).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/events/{id}/register", eventId), member, "USER")).andExpect(status().isCreated());
    }

    @Test
    void aDraftWhoseStartHasPassedCannotBePublished() throws Exception {
        String eventId = draft("Missed it", LocalDateTime.now(clock).plusHours(2));
        clock.set(START.plusSeconds(3 * 3600));

        mvc.perform(as(post("/api/v1/events/{id}/publish", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completedEventsMoveToThePastAndKeepTheirNumbers() throws Exception {
        String eventId = draft("Sunday long run", LocalDateTime.now(clock).plusDays(1));
        mvc.perform(as(post("/api/v1/events/{id}/publish", eventId), ownerId, "USER", "CLUB_OWNER")).andExpect(status().isOk());
        String registration = json.readTree(mvc.perform(as(post("/api/v1/events/{id}/register", eventId), member, "USER"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("registrationId").asString();
        mvc.perform(as(post("/api/v1/events/{id}/register", eventId), fan, "USER")).andExpect(status().isCreated());

        mvc.perform(as(post("/api/v1/events/{id}/complete", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_STARTED"));

        // event day: one of the two shows up
        clock.set(START.plusSeconds(24 * 3600));
        String code = json.readTree(mvc.perform(as(get("/api/v1/events/registrations/{id}/ticket", registration), member, "USER"))
                .andReturn().getResponse().getContentAsString()).get("ticketCode").asString();
        mvc.perform(as(post("/api/v1/events/{id}/check-in", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketCode\":\"%s\"}".formatted(code)))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/v1/events/{id}/stats", eventId), member, "USER")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/events/{id}/stats", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.checkedIn").value(1))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.attendanceRate").value(0.5));

        clock.set(START.plusSeconds(28 * 3600));
        mvc.perform(as(post("/api/v1/events/{id}/complete", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.registrationState").value("PAST"));
        // finished: no more edits, but everything stays readable
        mvc.perform(as(put("/api/v1/events/{id}", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_COMPLETED"));
        mvc.perform(as(get("/api/v1/events/{id}/registrations", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(get("/api/v1/events/club/{id}", clubId).param("upcoming", "true"), member, "USER"))
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(eventId)).doesNotExist());
        mvc.perform(as(get("/api/v1/events/club/{id}", clubId).param("upcoming", "false"), member, "USER"))
                .andExpect(jsonPath("$.items[?(@.id=='%s')].status".formatted(eventId)).value("COMPLETED"));

        draft("Next month", LocalDateTime.now(clock).plusDays(30));
        mvc.perform(as(get("/api/v1/events/club/{id}/stats", clubId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.drafts").value(1))
                .andExpect(jsonPath("$.registrations").value(2))
                .andExpect(jsonPath("$.checkIns").value(1))
                .andExpect(jsonPath("$.attendanceRate").value(0.5))
                .andExpect(jsonPath("$.events.length()").value(2));
        mvc.perform(as(get("/api/v1/events/club/{id}/stats", clubId), otherOwner, "USER", "CLUB_OWNER"))
                .andExpect(status().isForbidden());
    }
}

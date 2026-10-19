package com.theclub.eventservice.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.theclub.common.security.JwtService;
import com.theclub.eventservice.MutableClock;
import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.ClubClient.ClubInfo;
import com.theclub.eventservice.client.ClubClient.ClubSummary;
import com.theclub.eventservice.client.ClubClient.Membership;
import com.theclub.eventservice.client.NotificationClient;
import com.theclub.eventservice.client.NotificationClient.NotificationRequest;
import com.theclub.eventservice.client.UserClient;
import com.theclub.eventservice.client.UserClient.UserDetails;
import com.theclub.eventservice.client.UserClient.UserSummary;
import com.theclub.eventservice.payment.MockPaymentGateway;
import com.theclub.eventservice.payment.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EventFlowIntegrationTest.ClockConfig.class)
class EventFlowIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** 2026-10-10T10:00 local. */
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
    @Autowired
    PaymentGateway paymentGateway;

    @MockitoBean
    ClubClient clubClient;
    @MockitoBean
    UserClient userClient;
    @MockitoBean
    NotificationClient notificationClient;

    final UUID clubId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock.set(START);
        reset(clubClient, userClient, notificationClient);
        when(clubClient.getClub(clubId)).thenReturn(new ClubInfo(clubId, "Chennai Runners", ownerId, 2));
        when(clubClient.membership(clubId, memberId)).thenReturn(new Membership(true, "MEMBER"));
        when(clubClient.summaries(any())).thenReturn(List.of(new ClubSummary(clubId, "Chennai Runners", null)));
        when(userClient.summaries(any())).thenReturn(List.of(new UserSummary(memberId, "Asha", null)));
        when(notificationClient.create(any())).thenReturn(new NotificationClient.NotificationCreated(UUID.randomUUID()));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID userId, String... roles) {
        String token = jwtService.issueAccessToken(userId, "asha@example.com", "Asha", List.of(roles));
        return request.header("Authorization", "Bearer " + token);
    }

    private JsonNode body(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String createEvent(String extraJson) throws Exception {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(2);
        JsonNode event = body(as(post("/api/v1/events"), ownerId, "USER", "CLUB_OWNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clubId":"%s","title":"Sunday Long Run","location":"Marina Beach","date":"%s"%s}
                        """.formatted(clubId, start, extraJson)), 201);
        assertThat(event.get("startTime").asText()).startsWith(start.toString().substring(0, 16));
        return event.get("id").asText();
    }

    @Test
    void freeEventFromRegistrationToMonthlyLeaderboard() throws Exception {
        mvc.perform(get("/api/v1/events/club/{clubId}", clubId)).andExpect(status().isUnauthorized());
        mvc.perform(as(post("/api/v1/events"), memberId, "USER").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clubId\":\"%s\",\"title\":\"x\",\"startTime\":\"2026-12-01T06:00:00\"}".formatted(clubId)))
                .andExpect(status().isForbidden());

        String eventId = createEvent(",\"points\":25");

        mvc.perform(as(get("/api/v1/events/{id}", eventId), memberId, "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(true))
                .andExpect(jsonPath("$.myRegistration").doesNotExist());

        JsonNode registration = body(as(post("/api/v1/events/{id}/register", eventId), memberId, "USER"), 201);
        assertThat(registration.get("status").asText()).isEqualTo("CONFIRMED");
        String registrationId = registration.get("registrationId").asText();
        mvc.perform(as(post("/api/v1/events/{id}/register", eventId), memberId, "USER")).andExpect(status().isConflict());

        ArgumentCaptor<NotificationRequest> notification = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient).create(notification.capture());
        assertThat(notification.getValue().type()).isEqualTo("EVENT_REGISTRATION_CONFIRMED");
        assertThat(notification.getValue().email().to()).isEqualTo("asha@example.com");
        assertThat(notification.getValue().email().template()).isEqualTo("registration-confirmed");
        assertThat(notification.getValue().email().variables()).containsEntry("qrCid", "ticket-qr");
        assertThat(notification.getValue().email().attachments().get(0).contentId()).isEqualTo("ticket-qr");

        mvc.perform(as(get("/api/v1/events/me/registrations"), memberId, "USER"))
                .andExpect(jsonPath("$[0].registrationId").value(registrationId))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        JsonNode ticket = body(as(get("/api/v1/events/registrations/{id}/ticket", registrationId), memberId, "USER"), 200);
        String ticketCode = ticket.get("ticketCode").asText();
        assertThat(ticket.get("qrCodeBase64").asText()).isNotBlank();
        mvc.perform(as(get("/api/v1/events/registrations/{id}/ticket.png", registrationId), memberId, "USER"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(as(get("/api/v1/events/registrations/{id}/ticket", registrationId), UUID.randomUUID(), "USER"))
                .andExpect(status().isForbidden());

        String scanned = "THECLUB-TICKET:" + ticketCode;
        mvc.perform(as(post("/api/v1/events/{id}/check-in", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketCode\":\"%s\"}".formatted(scanned)))
                .andExpect(status().isConflict());

        clock.advance(Duration.ofDays(2).minusHours(1));
        mvc.perform(as(post("/api/v1/events/{id}/check-in", eventId), memberId, "USER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketCode\":\"%s\"}".formatted(scanned)))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/events/{id}/check-in", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketCode\":\"%s\"}".formatted(scanned)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.pointsAwarded").value(25));
        mvc.perform(as(post("/api/v1/events/{id}/check-in", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketCode\":\"%s\"}".formatted(ticketCode)))
                .andExpect(status().isConflict());

        mvc.perform(as(get("/api/v1/events/{id}/registrations", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(jsonPath("$[0].status").value("CHECKED_IN"));
        mvc.perform(as(get("/api/v1/participation/me"), memberId, "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsAttended").value(1))
                .andExpect(jsonPath("$.totalPoints").value(25))
                .andExpect(jsonPath("$.stars").value(0));
        mvc.perform(as(get("/api/v1/participation/clubs/{clubId}", clubId), memberId, "USER"))
                .andExpect(jsonPath("$[0].userId").value(memberId.toString()))
                .andExpect(jsonPath("$[0].eventsAttended").value(1));

        mvc.perform(as(get("/api/v1/leaderboards/monthly"), memberId, "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.topIndividuals[0].rank").value(1))
                .andExpect(jsonPath("$.topIndividuals[0].userId").value(memberId.toString()))
                .andExpect(jsonPath("$.topIndividuals[0].name").value("Asha"))
                .andExpect(jsonPath("$.topIndividuals[0].points").value(25))
                .andExpect(jsonPath("$.topClubs[0].name").value("Chennai Runners"))
                .andExpect(jsonPath("$.topClubs[0].participations").value(1));
        mvc.perform(as(get("/api/v1/leaderboards/monthly?month=2026-09"), memberId, "USER"))
                .andExpect(jsonPath("$.topIndividuals").isEmpty());

        mvc.perform(get("/internal/events/{id}", eventId).header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubId").value(clubId.toString()));
        mvc.perform(as(get("/internal/events/{id}", eventId), ownerId, "ADMIN")).andExpect(status().isForbidden());
    }

    @Test
    void paidEventConfirmsAfterVerifiedPayment() throws Exception {
        String eventId = createEvent(",\"price\":349.50,\"capacity\":10");

        JsonNode registration = body(as(post("/api/v1/events/{id}/register", eventId), memberId, "USER"), 201);
        assertThat(registration.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        JsonNode payment = registration.get("payment");
        assertThat(payment.get("provider").asText()).isEqualTo("MOCK");
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo("349.50");
        String paymentId = payment.get("paymentId").asText();

        mvc.perform(as(get("/api/v1/events/{id}", eventId), memberId, "USER"))
                .andExpect(jsonPath("$.myRegistration").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.spotsLeft").value(9))
                .andExpect(jsonPath("$.registeredCount").value(0));

        String signature = body(as(post("/api/v1/payments/{id}/mock-signature", paymentId), memberId, "USER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"providerPaymentId\":\"pay_123\"}"), 200)
                .get("signature").asText();

        mvc.perform(as(post("/api/v1/payments/{id}/confirm", paymentId), UUID.randomUUID(), "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerPaymentId\":\"pay_123\",\"signature\":\"%s\"}".formatted(signature)))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/payments/{id}/confirm", paymentId), memberId, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerPaymentId\":\"pay_123\",\"signature\":\"%s\"}".formatted(signature)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.registrationStatus").value("CONFIRMED"));

        mvc.perform(as(get("/api/v1/payments/{id}", paymentId), memberId, "USER"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(as(get("/api/v1/events/{id}", eventId), memberId, "USER"))
                .andExpect(jsonPath("$.myRegistration").value("CONFIRMED"))
                .andExpect(jsonPath("$.registeredCount").value(1));
        verify(notificationClient).create(any());
        mvc.perform(as(post("/api/v1/events/{id}/registrations/me", eventId).with(r -> {
                    r.setMethod("DELETE");
                    return r;
                }), memberId, "USER"))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationRespectsOpeningTimeAndMembership() throws Exception {
        LocalDateTime opens = LocalDateTime.now(clock).plusDays(1);
        String eventId = createEvent(",\"registrationOpensAt\":\"%s\"".formatted(opens));

        mvc.perform(as(post("/api/v1/events/{id}/register", eventId), memberId, "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Registration opens at " + opens.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        UUID outsider = UUID.randomUUID();
        when(clubClient.membership(clubId, outsider)).thenReturn(new Membership(false, null));
        mvc.perform(as(post("/api/v1/events/{id}/register", eventId), outsider, "USER"))
                .andExpect(status().isForbidden());

        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/v1/events/{id}/registration-window", eventId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationOpensAt\":\"%s\"}".formatted(LocalDateTime.now(clock).minusMinutes(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(true));
        mvc.perform(as(post("/api/v1/events/{id}/register", eventId), memberId, "USER"))
                .andExpect(status().isCreated());
    }

    @Test
    void webhookSettlesAPaymentTheClientNeverConfirmedAndOwnerCanRefund() throws Exception {
        when(userClient.get(memberId)).thenReturn(new UserDetails(memberId, "Asha", "asha@example.com"));
        String eventId = createEvent(",\"price\":349.50,\"capacity\":10");
        JsonNode checkout = body(as(post("/api/v1/events/{id}/register", eventId), memberId, "USER"), 201).get("payment");
        String paymentId = checkout.get("paymentId").asText();
        String webhook = """
                {"entity":"event","event":"payment.captured","payload":{"payment":{"entity":
                {"id":"pay_wh_1","order_id":"%s","amount":34950,"currency":"INR","status":"captured"}}}}
                """.formatted(checkout.get("providerOrderId").asText());
        String signature = ((MockPaymentGateway) paymentGateway).signWebhook(webhook.getBytes(StandardCharsets.UTF_8));

        // signature over a different body is rejected
        mvc.perform(post("/api/v1/payments/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .content(webhook.replace("34950", "1")).header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", "evt_1"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/v1/payments/{id}", paymentId), memberId, "USER"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        // valid delivery needs no JWT; redelivery is a no-op
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/payments/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                            .content(webhook).header("X-Razorpay-Signature", signature)
                            .header("X-Razorpay-Event-Id", "evt_1"))
                    .andExpect(status().isOk());
        }
        mvc.perform(as(get("/api/v1/payments/{id}", paymentId), memberId, "USER"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.registrationStatus").value("CONFIRMED"));
        ArgumentCaptor<NotificationRequest> confirmation = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient, times(1)).create(confirmation.capture());
        assertThat(confirmation.getValue().type()).isEqualTo("EVENT_REGISTRATION_CONFIRMED");
        assertThat(confirmation.getValue().email().to()).isEqualTo("asha@example.com");

        mvc.perform(as(get("/api/v1/events/{id}/registrations", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(jsonPath("$[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$[0].paymentStatus").value("SUCCEEDED"));

        // refunds are for the club owner, not the member
        mvc.perform(as(post("/api/v1/payments/{id}/refund", paymentId), memberId, "USER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"please\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/payments/{id}/refund", paymentId), ownerId, "USER", "CLUB_OWNER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Injured\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundReason").value("Injured"))
                .andExpect(jsonPath("$.refundedAt").exists())
                .andExpect(jsonPath("$.registrationStatus").value("CANCELLED"));
        mvc.perform(as(post("/api/v1/payments/{id}/refund", paymentId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(status().isConflict());
        mvc.perform(as(get("/api/v1/events/{id}/registrations", eventId), ownerId, "USER", "CLUB_OWNER"))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].paymentStatus").value("REFUNDED"));
    }
}

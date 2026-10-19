package com.theclub.notificationservice.controller;

import tools.jackson.databind.ObjectMapper;
import com.theclub.common.security.JwtService;
import com.theclub.notificationservice.email.EmailDispatcher;
import com.theclub.notificationservice.model.EmailOutbox;
import com.theclub.notificationservice.model.EmailStatus;
import com.theclub.notificationservice.repository.EmailOutboxRepository;
import com.theclub.notificationservice.repository.EmailSuppressionRepository;
import com.theclub.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailOutboxIntegrationTest {

    private static final String KEY = "test-internal-key";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;
    @Autowired
    EmailOutboxRepository outbox;
    @Autowired
    EmailSuppressionRepository suppressions;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    EmailDispatcher dispatcher;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
        suppressions.deleteAll();
    }

    private String admin() {
        return "Bearer " + jwtService.issueAccessToken(UUID.randomUUID(), "a@x.com", "A", List.of("USER", "ADMIN"));
    }

    private org.springframework.test.web.servlet.ResultActions internal(UUID userId, String email) throws Exception {
        return mvc.perform(post("/internal/notifications").header("X-Internal-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\",\"type\":\"PASSWORD_RESET\",\"message\":\"m\",\"email\":%s}".formatted(userId, email)));
    }

    @Test
    void templatedEmailIsQueuedWithTheNotificationAndDeliveredByTheDispatcher() throws Exception {
        UUID userId = UUID.randomUUID();
        internal(userId, """
                {"to":"asha@example.com","template":"password-reset",
                 "variables":{"name":"Asha","link":"https://app.example/reset-password?token=abc"}}""")
                .andExpect(status().isAccepted());

        List<EmailOutbox> queued = outbox.findAll();
        assertThat(queued).hasSize(1);
        EmailOutbox row = queued.getFirst();
        assertThat(row.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(row.getSubject()).isEqualTo("Reset your The Club password");
        assertThat(row.getHtmlBody()).contains("https://app.example/reset-password?token=abc");
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 5)).getTotalElements())
                .isEqualTo(1);

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

        EmailOutbox sent = outbox.findById(row.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(sent.getProvider()).isEqualTo("log");
        assertThat(sent.getAttempts()).isEqualTo(1);
        assertThat(dispatcher.dispatchOnce()).isZero();
    }

    @Test
    void anInvalidEmailRollsBackTheNotificationToo() throws Exception {
        UUID userId = UUID.randomUUID();
        internal(userId, "{\"to\":\"asha@example.com\",\"template\":\"no-such-template\"}")
                .andExpect(status().isBadRequest());
        internal(userId, "{\"to\":\"asha@example.com\",\"template\":\"password-reset\",\"variables\":{\"name\":\"A\"}}")
                .andExpect(status().isBadRequest());
        internal(userId, "{\"to\":\"asha@example.com\"}")
                .andExpect(status().isBadRequest());

        assertThat(outbox.count()).isZero();
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 5)).getTotalElements())
                .isZero();
    }

    @Test
    void rawEmailsStillWorkForOlderCallers() throws Exception {
        internal(UUID.randomUUID(), "{\"to\":\"x@example.com\",\"subject\":\"Hi\",\"body\":\"<p>Hello <b>you</b></p>\"}")
                .andExpect(status().isAccepted());

        EmailOutbox row = outbox.findAll().getFirst();
        assertThat(row.getTemplate()).isNull();
        assertThat(row.getTextBody()).isEqualTo("Hello you\n");
    }

    @Test
    void adminsCanListAndRetryFailedEmails() throws Exception {
        internal(UUID.randomUUID(), "{\"to\":\"bounced@example.com\",\"subject\":\"Hi\",\"body\":\"<p>x</p>\"}")
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/notifications/webhooks/zeptomail").param("token", "test-webhook-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"event_name":["hardbounce"],"event_message":[{"email_info":{"to":[{"email_address":{"address":"bounced@example.com"}}]},
                                 "event_data":[{"details":[{"bounced_recipient":"Bounced@Example.com","reason":"mailbox does not exist"}]}]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppressed").value(1));

        dispatcher.dispatchOnce();
        EmailOutbox row = outbox.findAll().getFirst();
        assertThat(row.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(row.getLastError()).contains("suppressed");

        String user = "Bearer " + jwtService.issueAccessToken(UUID.randomUUID(), "u@x.com", "U", List.of("USER"));
        mvc.perform(get("/api/v1/notifications/admin/email-outbox").header("Authorization", user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications/admin/email-outbox").param("status", "FAILED").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].recipient").value("bounced@example.com"))
                .andExpect(jsonPath("$.items[0].htmlBody").doesNotExist());

        mvc.perform(post("/api/v1/notifications/admin/email-outbox/{id}/retry", row.getId()).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
        mvc.perform(post("/api/v1/notifications/admin/email-outbox/{id}/retry", row.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void webhookRequiresTheSharedToken() throws Exception {
        String body = "{\"event_name\":[\"hardbounce\"],\"bounced_recipient\":\"x@example.com\"}";
        mvc.perform(post("/api/v1/notifications/webhooks/zeptomail").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/notifications/webhooks/zeptomail").param("token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/notifications/webhooks/zeptomail").header("X-Webhook-Token", "test-webhook-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"event_name\":[\"softbounce\"],\"bounced_recipient\":\"x@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppressed").value(0));
        assertThat(suppressions.count()).isZero();
    }
}

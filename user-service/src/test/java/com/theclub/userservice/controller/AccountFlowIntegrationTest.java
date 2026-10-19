package com.theclub.userservice.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.theclub.userservice.client.NotificationClient;
import com.theclub.userservice.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountFlowIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Str0ng-pass!";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String INTERNAL_KEY = "test-internal-key";

    @Autowired
    ObjectMapper json;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private List<NotificationClient.Notification> sent(int expected) {
        ArgumentCaptor<NotificationClient.Notification> captor = ArgumentCaptor.forClass(NotificationClient.Notification.class);
        verify(notificationClient, times(expected)).send(captor.capture());
        return captor.getAllValues();
    }

    private static String tokenIn(NotificationClient.Notification notification) {
        Matcher m = TOKEN.matcher(String.valueOf(notification.email().variables().get("link")));
        assertThat(m.find()).as("email contains a token link").isTrue();
        return m.group(1);
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode credentials(String email) throws Exception {
        return json.readTree(mvc.perform(get("/internal/users/by-email/{email}", email).header("X-Internal-Key", INTERNAL_KEY))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void registrationEmailsAVerificationLinkThatWorksOnce() throws Exception {
        String id = register("verify@example.com");

        NotificationClient.Notification email = sent(1).getFirst();
        assertThat(email.userId().toString()).isEqualTo(id);
        assertThat(email.type()).isEqualTo("EMAIL_VERIFICATION");
        assertThat(email.email().to()).isEqualTo("verify@example.com");
        assertThat(email.email().template()).isEqualTo("email-verification");
        assertThat(email.email().variables().get("link").toString()).startsWith("http://localhost:3000/verify-email?token=");
        assertThat(credentials("verify@example.com").get("emailVerified").asBoolean()).isFalse();

        String token = tokenIn(email);
        postJson("/api/v1/users/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isNoContent());

        assertThat(credentials("verify@example.com").get("emailVerified").asBoolean()).isTrue();
        mvc.perform(get("/api/v1/users/me").header("Authorization", tokenFor(id, "USER")))
                .andExpect(jsonPath("$.emailVerified").value(true));
        postJson("/api/v1/users/verify-email", "{\"token\":\"" + token + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This link is invalid or has expired"));
    }

    @Test
    void verificationLinkExpiresAfter48Hours() throws Exception {
        register("late@example.com");
        String token = tokenIn(sent(1).getFirst());

        clock.advance(Duration.ofHours(48).plusSeconds(1));
        postJson("/api/v1/users/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void resendIsThrottledAndSupersedesTheOldLink() throws Exception {
        register("resend@example.com");
        postJson("/api/v1/users/verify-email/resend", "{\"email\":\"resend@example.com\"}").andExpect(status().isAccepted());
        // still within the 60 s throttle: no second email
        String first = tokenIn(sent(1).getFirst());

        clock.advance(Duration.ofSeconds(61));
        postJson("/api/v1/users/verify-email/resend", "{\"email\":\"RESEND@example.com\"}").andExpect(status().isAccepted());
        String second = tokenIn(sent(2).get(1));

        postJson("/api/v1/users/verify-email", "{\"token\":\"" + first + "\"}").andExpect(status().isBadRequest());
        postJson("/api/v1/users/verify-email", "{\"token\":\"" + second + "\"}").andExpect(status().isNoContent());
    }

    @Test
    void requestsForUnknownEmailsAreAcceptedSilently() throws Exception {
        postJson("/api/v1/users/verify-email/resend", "{\"email\":\"nobody@example.com\"}").andExpect(status().isAccepted());
        postJson("/api/v1/users/password-reset/request", "{\"email\":\"nobody@example.com\"}").andExpect(status().isAccepted());
        verify(notificationClient, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passwordResetFlowChangesPasswordVerifiesEmailAndRevokesSessions() throws Exception {
        String id = register("reset@example.com");
        postJson("/api/v1/users/password-reset/request", "{\"email\":\"reset@example.com\"}").andExpect(status().isAccepted());
        NotificationClient.Notification resetEmail = sent(2).get(1);
        assertThat(resetEmail.type()).isEqualTo("PASSWORD_RESET");
        assertThat(resetEmail.email().template()).isEqualTo("password-reset");
        assertThat(resetEmail.email().variables().get("link").toString()).startsWith("http://localhost:3000/reset-password?token=");
        String token = tokenIn(resetEmail);

        // a rejected password doesn't burn the link
        postJson("/api/v1/users/password-reset/confirm", "{\"token\":\"" + token + "\",\"newPassword\":\"password123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("This password is too common"));
        postJson("/api/v1/users/password-reset/confirm", "{\"token\":\"" + token + "\",\"newPassword\":\"N3w-Secret-pass\"}")
                .andExpect(status().isNoContent());

        JsonNode creds = credentials("reset@example.com");
        assertThat(passwordEncoder.matches("N3w-Secret-pass", creds.get("passwordHash").asText())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, creds.get("passwordHash").asText())).isFalse();
        assertThat(creds.get("emailVerified").asBoolean()).isTrue();
        verify(authClient).revokeSessions(UUID.fromString(id));
        assertThat(sent(3).get(2).type()).isEqualTo("PASSWORD_CHANGED");

        postJson("/api/v1/users/password-reset/confirm", "{\"token\":\"" + token + "\",\"newPassword\":\"An0ther-pass!\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetLinkExpiresAfter30MinutesAndCannotVerifyEmail() throws Exception {
        register("expire@example.com");
        postJson("/api/v1/users/password-reset/request", "{\"email\":\"expire@example.com\"}");
        String token = tokenIn(sent(2).get(1));

        postJson("/api/v1/users/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isBadRequest());
        clock.advance(Duration.ofMinutes(30).plusSeconds(1));
        postJson("/api/v1/users/password-reset/confirm", "{\"token\":\"" + token + "\",\"newPassword\":\"N3w-Secret-pass\"}")
                .andExpect(status().isBadRequest());
        verify(authClient, never()).revokeSessions(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changePasswordChecksCurrentPasswordAndPolicyThenRevokesSessions() throws Exception {
        String id = register("change@example.com");
        String auth = tokenFor(id, "USER");

        mvc.perform(put("/api/v1/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"N3w-Secret-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("is incorrect"));
        mvc.perform(put("/api/v1/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        mvc.perform(put("/api/v1/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"qwerty123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("This password is too common"));
        verify(authClient, never()).revokeSessions(org.mockito.ArgumentMatchers.any());

        mvc.perform(put("/api/v1/users/me/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"N3w-Secret-pass\"}"))
                .andExpect(status().isNoContent());
        verify(authClient).revokeSessions(UUID.fromString(id));
        verify(notificationClient, atLeastOnce()).send(org.mockito.ArgumentMatchers.argThat(n -> "PASSWORD_CHANGED".equals(n.type())));
        mvc.perform(put("/api/v1/users/me/password")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationEnforcesThePasswordPolicy() throws Exception {
        postJson("/api/v1/users/register", "{\"name\":\"A\",\"email\":\"weak@example.com\",\"password\":\"password123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("This password is too common"));
        postJson("/api/v1/users/register", "{\"name\":\"A\",\"email\":\"same@example.com\",\"password\":\"same@example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void emailFailuresDoNotBreakRegistration() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(notificationClient)
                .send(org.mockito.ArgumentMatchers.any());
        register("resilient@example.com");
    }
}

package com.theclub.notificationservice.email;

import com.theclub.common.web.ApiException;
import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailComposerTest {

    private final EmailRenderer renderer = new EmailRenderer();

    private static MailProperties props(String redirect) {
        return new MailProperties("log", false, null, null, redirect, null, null, null);
    }

    private EmailComposer composer(String redirect) {
        return new EmailComposer(renderer, props(redirect));
    }

    private static EmailRequest template(String to, String template, Map<String, Object> variables) {
        return new EmailRequest(to, null, null, template, variables, List.of());
    }

    @Test
    void rendersTemplatesIntoSubjectHtmlAndText() {
        EmailComposer.Composed c = composer(null).compose(template("Asha@Example.com", "registration-confirmed",
                Map.of("eventTitle", "Sunday Long Run", "when", "Sun, 20 Oct 2026 at 06:00 AM",
                        "location", "Marina", "ticketCode", "ABC123", "qrCid", "ticket-qr")));

        assertThat(c.recipient()).isEqualTo("Asha@Example.com");
        assertThat(c.subject()).isEqualTo("Your ticket: Sunday Long Run");
        assertThat(c.template()).isEqualTo("registration-confirmed");
        assertThat(c.html()).contains("Sunday Long Run", "Marina", "ABC123", "src=\"cid:ticket-qr\"", "#0f766e",
                "You received this email because");
        assertThat(c.text()).contains("Sunday Long Run", "Where: Marina", "Ticket code: ABC123").doesNotContain("<");
    }

    @Test
    void escapesCallerSuppliedValues() {
        EmailComposer.Composed c = composer(null).compose(template("a@b.com", "email-verification",
                Map.of("name", "<script>alert(1)</script>", "link", "https://app.example/verify-email?token=x&y=1")));

        assertThat(c.html()).doesNotContain("<script>").contains("&lt;script&gt;")
                .contains("href=\"https://app.example/verify-email?token=x&amp;y=1\"");
        assertThat(c.text()).contains("https://app.example/verify-email?token=x&y=1");
    }

    @Test
    void optionalSectionsAreLeftOutWhenNotGiven() {
        EmailComposer.Composed c = composer(null).compose(template("a@b.com", "payment-refunded",
                Map.of("eventTitle", "10K", "amount", "499.00 INR")));

        assertThat(c.html()).doesNotContain("Reason:");
        assertThat(c.text()).doesNotContain("Reason:");
    }

    @Test
    void unknownTemplatesAndMissingVariablesAreRejected() {
        assertThatThrownBy(() -> composer(null).compose(template("a@b.com", "nope", Map.of())))
                .isInstanceOf(ApiException.class).hasMessageContaining("Unknown email template");
        assertThatThrownBy(() -> composer(null).compose(template("a@b.com", "password-reset", Map.of("name", "A"))))
                .isInstanceOf(ApiException.class).hasMessageContaining("[link]");
    }

    @Test
    void subjectLineBreaksAreRemovedToPreventHeaderInjection() {
        EmailComposer.Composed c = composer(null).compose(new EmailRequest("a@b.com",
                "Hello\r\nBcc: victim@example.com\nX", "<p>Body</p><p>Two</p>", List.of()));

        assertThat(c.subject()).isEqualTo("Hello Bcc: victim@example.com X").doesNotContain("\n", "\r");
        assertThat(c.text()).isEqualTo("Body\nTwo\n");
        assertThat(c.template()).isNull();
    }

    @Test
    void invalidRecipientsAreRejected() {
        for (String bad : List.of("not-an-email", "a@b.com\r\nBcc: x@y.com", " ")) {
            assertThatThrownBy(() -> composer(null).compose(new EmailRequest(bad, "s", "<p>b</p>", List.of())))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void stagingRedirectSendsEverythingToOneAddressAndKeepsTheOriginalInTheSubject() {
        EmailComposer.Composed c = composer("qa@theclub.example").compose(template("member@gmail.com",
                "password-changed", Map.of("name", "Asha")));

        assertThat(c.recipient()).isEqualTo("qa@theclub.example");
        assertThat(c.subject()).isEqualTo("[to member@gmail.com] Your The Club password was changed");
    }

    @Test
    void everyTemplateRenders() {
        Map<String, Object> all = Map.of("name", "Asha", "link", "https://x.example/a", "eventTitle", "Run",
                "when", "Sun", "ticketCode", "T1", "amount", "1 INR", "decision", "Approved", "message", "Welcome!");
        for (EmailTemplate t : EmailTemplate.values()) {
            EmailComposer.Composed c = composer(null).compose(template("a@b.com", t.id(), all));
            assertThat(c.html()).as(t.id()).contains("The Club").contains(t.footer());
            assertThat(c.text()).as(t.id()).contains(t.footer());
            assertThat(c.subject()).as(t.id()).doesNotContain("{");
        }
    }
}

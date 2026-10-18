package com.theclub.notificationservice.email;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Every transactional email the platform sends. Callers pass the template id and its variables; the
 * subject and bodies come from {@code templates/email/{html,text}/<id>}.
 *
 * @param subject   subject line; {@code {name}} placeholders are replaced with variables.
 * @param required  variables the caller must provide.
 * @param footer    why the recipient got this email (shown in the footer).
 */
public enum EmailTemplate {

    EMAIL_VERIFICATION("email-verification", "Verify your email for The Club",
            List.of("name", "link"), "you signed up for The Club with this address."),
    PASSWORD_RESET("password-reset", "Reset your The Club password",
            List.of("name", "link"), "someone asked to reset the password of your The Club account."),
    PASSWORD_CHANGED("password-changed", "Your The Club password was changed",
            List.of("name"), "the password of your The Club account was changed."),
    REGISTRATION_CONFIRMED("registration-confirmed", "Your ticket: {eventTitle}",
            List.of("eventTitle", "when", "ticketCode"), "you registered for an event on The Club."),
    PAYMENT_REFUNDED("payment-refunded", "Refund for {eventTitle}",
            List.of("eventTitle", "amount"), "you paid for an event on The Club."),
    EVENT_CANCELLED("event-cancelled", "Cancelled: {eventTitle}",
            List.of("eventTitle", "when"), "you registered for an event on The Club."),
    OWNER_APPLICATION_DECISION("owner-application-decision", "Your club owner application: {decision}",
            List.of("name", "decision"), "you applied to become a club owner on The Club.");

    private final String id;
    private final String subject;
    private final List<String> required;
    private final String footer;

    EmailTemplate(String id, String subject, List<String> required, String footer) {
        this.id = id;
        this.subject = subject;
        this.required = required;
        this.footer = footer;
    }

    public String id() {
        return id;
    }

    public String subject() {
        return subject;
    }

    public List<String> required() {
        return required;
    }

    public String footer() {
        return footer;
    }

    public static Optional<EmailTemplate> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }
}

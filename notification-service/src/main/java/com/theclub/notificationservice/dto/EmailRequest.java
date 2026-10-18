package com.theclub.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * An email to send with a notification. Either name a {@code template} (preferred: branded HTML + text,
 * see {@code EmailTemplate}) with its {@code variables}, or give a raw {@code subject} and HTML {@code body}.
 */
public record EmailRequest(
        @NotBlank @Email @Size(max = 254) String to,
        @Size(max = 255) String subject,
        String body,
        @Size(max = 80) String template,
        @Size(max = 30) Map<String, Object> variables,
        @Valid @Size(max = 10) List<EmailAttachment> attachments) {

    public EmailRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    /** Raw (non-template) email. */
    public EmailRequest(String to, String subject, String body, List<EmailAttachment> attachments) {
        this(to, subject, body, null, null, attachments);
    }

    @JsonIgnore
    public boolean isTemplated() {
        return template != null && !template.isBlank();
    }

    @JsonIgnore
    @AssertTrue(message = "give either a template or a subject and body")
    public boolean isTemplateOrContent() {
        return isTemplated() || (subject != null && !subject.isBlank() && body != null && !body.isBlank());
    }
}

package com.theclub.notificationservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param body HTML body.
 */
public record EmailRequest(
        @NotBlank @Email @Size(max = 254) String to,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String body,
        @Valid @Size(max = 10) List<EmailAttachment> attachments) {

    public EmailRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}

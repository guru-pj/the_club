package com.theclub.notificationservice.email;

import com.theclub.notificationservice.dto.EmailAttachment;

import java.util.List;

/**
 * A fully rendered email handed to an {@link EmailProvider}.
 *
 * @param reference our outbox id, passed to the provider so its logs and webhooks can be correlated.
 */
public record OutgoingEmail(String fromAddress, String fromName, String replyTo, String to, String subject,
                            String htmlBody, String textBody, List<EmailAttachment> attachments, String reference) {

    public OutgoingEmail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}

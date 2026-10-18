package com.theclub.notificationservice.service;

import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailAttachment;
import com.theclub.notificationservice.dto.EmailRequest;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Best-effort email delivery: runs asynchronously and never propagates failures to the caller.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public EmailSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    public void send(EmailRequest email) {
        if (!properties.enabled()) {
            log.info("Email disabled; would send '{}' to {} ({} attachment(s))",
                    email.subject(), email.to(), email.attachments().size());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.body(), true);
            for (EmailAttachment attachment : email.attachments()) {
                ByteArrayResource content = new ByteArrayResource(Base64.getDecoder().decode(attachment.base64Content()));
                if (attachment.inline()) {
                    String contentId = attachment.contentId() != null ? attachment.contentId() : attachment.filename();
                    helper.addInline(contentId, content, attachment.contentType());
                } else {
                    helper.addAttachment(attachment.filename(), content, attachment.contentType());
                }
            }
            mailSender.send(message);
            log.info("Sent email '{}' to {}", email.subject(), email.to());
        } catch (Exception e) {
            log.warn("Failed to send email '{}' to {}: {}", email.subject(), email.to(), e.getMessage());
        }
    }
}

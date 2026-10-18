package com.theclub.notificationservice.email;

import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailAttachment;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

/**
 * Sends through any SMTP server (Mailpit locally, or Zoho Mail / Workspace SMTP as a fallback to ZeptoMail).
 */
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;

    public SmtpEmailProvider(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public String name() {
        return MailProperties.SMTP;
    }

    @Override
    public String send(OutgoingEmail email) {
        MimeMessage message;
        try {
            message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (email.fromName() != null && !email.fromName().isBlank()) {
                helper.setFrom(email.fromAddress(), email.fromName());
            } else {
                helper.setFrom(email.fromAddress());
            }
            if (email.replyTo() != null && !email.replyTo().isBlank()) {
                helper.setReplyTo(email.replyTo());
            }
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.textBody(), email.htmlBody());
            for (EmailAttachment attachment : email.attachments()) {
                ByteArrayResource content = new ByteArrayResource(Base64.getDecoder().decode(attachment.base64Content()));
                if (attachment.inline()) {
                    helper.addInline(attachment.contentId() != null ? attachment.contentId() : attachment.filename(),
                            content, attachment.contentType());
                } else {
                    helper.addAttachment(attachment.filename(), content, attachment.contentType());
                }
            }
        } catch (MessagingException | UnsupportedEncodingException | IllegalArgumentException e) {
            throw new EmailDeliveryException("Could not build the message: " + e.getMessage(), false, e);
        }
        try {
            mailSender.send(message);
            return null;
        } catch (MailParseException e) {
            throw new EmailDeliveryException("SMTP rejected the message: " + e.getMessage(), false, e);
        } catch (MailException e) {
            throw new EmailDeliveryException("SMTP delivery failed: " + e.getMessage(), true, e);
        }
    }
}

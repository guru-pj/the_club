package com.theclub.notificationservice.service;

import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailAttachment;
import com.theclub.notificationservice.dto.EmailRequest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @Mock
    JavaMailSender mailSender;

    private final EmailRequest email = new EmailRequest("runner@example.com", "Your ticket",
            "<p>See you</p><img src=\"cid:qr\">",
            List.of(new EmailAttachment("ticket.png", "image/png",
                    Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), true, "qr")));

    @Test
    void disabledMailOnlyLogs() {
        new EmailSender(mailSender, new MailProperties(false, null)).send(email);
        verifyNoInteractions(mailSender);
    }

    @Test
    void enabledMailSendsMimeMessage() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        new EmailSender(mailSender, new MailProperties(true, null)).send(email);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("Your ticket");
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("runner@example.com");
    }

    @Test
    void sendFailuresAreSwallowed() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> new EmailSender(mailSender, new MailProperties(true, null)).send(email))
                .doesNotThrowAnyException();
    }
}

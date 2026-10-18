package com.theclub.notificationservice.email;

import com.theclub.notificationservice.dto.EmailAttachment;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailProviderTest {

    @Mock
    JavaMailSender mailSender;

    private final OutgoingEmail email = new OutgoingEmail("no-reply@theclub.local", "The Club", null,
            "runner@example.com", "Your ticket", "<p>See you</p><img src=\"cid:qr\">", "See you\n",
            List.of(new EmailAttachment("ticket.png", "image/png",
                    Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), true, "qr")), "ref-1");

    @Test
    void sendsAMultipartMessageWithHtmlTextAndInlineImage() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        new SmtpEmailProvider(mailSender).send(email);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Your ticket");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("The Club");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("runner@example.com");
        assertThat(((Multipart) message.getContent()).getCount()).isPositive();
    }

    @Test
    void smtpOutagesAreRetryable() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> new SmtpEmailProvider(mailSender).send(email))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> assertThat(e.isRetryable()).isTrue());
    }
}

package com.theclub.notificationservice.email;

import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.model.EmailOutbox;
import com.theclub.notificationservice.repository.EmailSuppressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

    @Mock
    EmailOutboxService outbox;
    @Mock
    EmailProvider provider;
    @Mock
    EmailSuppressionRepository suppressions;
    @Mock
    EmailMetrics metrics;

    EmailDispatcher dispatcher;
    EmailOutbox email;

    @BeforeEach
    void setUp() {
        MailProperties props = new MailProperties("zeptomail", false, "The Club <no-reply@theclub.example>",
                null, null, null, null, null);
        dispatcher = new EmailDispatcher(outbox, provider, suppressions, metrics, props);
        email = new EmailOutbox("Runner@Example.com", "Hi", "password-reset", "<p>h</p>", "h", null, LocalDateTime.now());
        email.setAttempts(1);
        when(outbox.claimBatch(20)).thenReturn(List.of(email));
        when(provider.name()).thenReturn("zeptomail");
    }

    @Test
    void deliversAndMarksSent() {
        when(provider.send(any())).thenReturn("req-1");

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(provider).send(sent.capture());
        assertThat(sent.getValue().fromAddress()).isEqualTo("no-reply@theclub.example");
        assertThat(sent.getValue().fromName()).isEqualTo("The Club");
        assertThat(sent.getValue().reference()).isEqualTo(email.getId().toString());
        verify(outbox).markSent(email.getId(), "zeptomail", "req-1");
        verify(metrics).sent("zeptomail", "password-reset");
    }

    @Test
    void temporaryFailuresAreRetried() {
        when(provider.send(any())).thenThrow(new EmailDeliveryException("HTTP 503", true));
        when(outbox.markFailed(email.getId(), "zeptomail", "HTTP 503", true)).thenReturn(false);

        assertThat(dispatcher.dispatchOnce()).isZero();

        verify(outbox).markFailed(email.getId(), "zeptomail", "HTTP 503", true);
        verify(metrics).failed("zeptomail", "retry");
    }

    @Test
    void permanentFailuresAreNotRetried() {
        when(provider.send(any())).thenThrow(new EmailDeliveryException("HTTP 401 TM_3501", false));
        when(outbox.markFailed(email.getId(), "zeptomail", "HTTP 401 TM_3501", false)).thenReturn(true);

        dispatcher.dispatchOnce();

        verify(metrics).failed("zeptomail", "permanent");
    }

    @Test
    void suppressedRecipientsAreSkipped() {
        when(suppressions.existsByEmail("runner@example.com")).thenReturn(true);

        dispatcher.dispatchOnce();

        verify(provider, never()).send(any());
        verify(outbox).markFailed(eq(email.getId()), anyString(), eq("Recipient is suppressed after a hard bounce"), anyBoolean());
        verify(metrics).failed("zeptomail", "suppressed");
    }
}

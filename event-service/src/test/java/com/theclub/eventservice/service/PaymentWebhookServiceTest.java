package com.theclub.eventservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.model.PaymentWebhookEvent;
import com.theclub.eventservice.payment.MockPaymentGateway;
import com.theclub.eventservice.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentWebhookServiceTest {

    @Mock
    PaymentService paymentService;
    @Mock
    PaymentWebhookEventRepository processed;

    final MockPaymentGateway gateway = new MockPaymentGateway("webhook-secret");
    PaymentWebhookService service;

    static final String CAPTURED = """
            {"entity":"event","event":"payment.captured","payload":{"payment":{"entity":
            {"id":"pay_29QQoUBi66xm2f","order_id":"order_9A33XWu170gUtm","amount":34950,"currency":"INR","status":"captured"}}}}
            """;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookService(gateway, paymentService, processed, new ObjectMapper(), CLOCK);
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsOnlyTheSignatureOfTheExactBody() {
        byte[] body = bytes(CAPTURED);
        String signature = gateway.signWebhook(body);

        assertThat(service.signatureValid(body, signature)).isTrue();
        assertThat(service.signatureValid(body, signature.toUpperCase())).isTrue();
        assertThat(service.signatureValid(bytes(CAPTURED.replace("34950", "100")), signature)).isFalse();
        assertThat(service.signatureValid(body, new MockPaymentGateway("other-secret").signWebhook(body))).isFalse();
        assertThat(service.signatureValid(body, null)).isFalse();
    }

    @Test
    void capturedPaymentSettlesTheOrder() {
        assertThat(service.process("evt_1", bytes(CAPTURED))).isEqualTo(PaymentWebhookService.Outcome.PROCESSED);

        verify(paymentService).settleCapturedOrder("order_9A33XWu170gUtm", "pay_29QQoUBi66xm2f", 34950, "INR");
        ArgumentCaptor<PaymentWebhookEvent> saved = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(processed).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo("evt_1");
        assertThat(saved.getValue().getEventType()).isEqualTo("payment.captured");
    }

    @Test
    void orderPaidIsHandledLikeACapture() {
        service.process("evt_2", bytes(CAPTURED.replace("payment.captured", "order.paid")));

        verify(paymentService).settleCapturedOrder("order_9A33XWu170gUtm", "pay_29QQoUBi66xm2f", 34950, "INR");
    }

    @Test
    void duplicateDeliveriesAreNoOps() {
        when(processed.existsById("evt_1")).thenReturn(true);

        assertThat(service.process("evt_1", bytes(CAPTURED))).isEqualTo(PaymentWebhookService.Outcome.DUPLICATE);

        verifyNoInteractions(paymentService);
        verify(processed, never()).saveAndFlush(any());
    }

    @Test
    void deliveriesWithoutAnIdAreKeyedByBodyHash() {
        service.process(null, bytes(CAPTURED));

        ArgumentCaptor<PaymentWebhookEvent> saved = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(processed).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventId()).startsWith("sha256:").hasSize(7 + 64);
    }

    @Test
    void failedPaymentIsRecorded() {
        service.process("evt_3", bytes(CAPTURED.replace("payment.captured", "payment.failed")));

        verify(paymentService).markFailedByOrder("order_9A33XWu170gUtm");
        verify(paymentService, never()).settleCapturedOrder(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void processedRefundCompletesPendingRefund() {
        String body = """
                {"event":"refund.processed","payload":{"refund":{"entity":{"id":"rfnd_1","payment_id":"pay_1","amount":34950}}}}
                """;

        service.process("evt_4", bytes(body));

        verify(paymentService).markRefundProcessed("pay_1", "rfnd_1");
    }

    @Test
    void unknownEventsAreRecordedAndIgnored() {
        assertThat(service.process("evt_5", bytes("{\"event\":\"subscription.charged\",\"payload\":{}}")))
                .isEqualTo(PaymentWebhookService.Outcome.IGNORED);

        verify(processed).saveAndFlush(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    void malformedJsonIsABadRequest() {
        assertThatThrownBy(() -> service.process("evt_6", bytes("not json")))
                .isInstanceOf(ApiException.class);
    }
}

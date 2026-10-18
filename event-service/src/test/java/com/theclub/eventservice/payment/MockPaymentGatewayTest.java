package com.theclub.eventservice.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway("secret");

    @Test
    void verifiesOnlyMatchingSignatures() {
        String order = gateway.createOrder(BigDecimal.TEN, "INR", "receipt");
        String signature = gateway.sign(order, "pay_1");

        assertThat(order).startsWith("mock_order_");
        assertThat(signature).hasSize(64);
        assertThat(gateway.verify(order, "pay_1", signature)).isTrue();
        assertThat(gateway.verify(order, "pay_2", signature)).isFalse();
        assertThat(gateway.verify(order, "pay_1", null)).isFalse();
        assertThat(new MockPaymentGateway("other").verify(order, "pay_1", signature)).isFalse();
    }

    @Test
    void usesRazorpaySignatureScheme() {
        // Independently computed HMAC-SHA256 of "order_1|pay_1" keyed with "secret"
        String expected = "52115a0d3400de9e86aade1f1b6eba9e8974604f4e267a9e9a16633a4c8dd2cb";
        assertThat(HmacSigner.sign("secret", "order_1", "pay_1")).isEqualTo(expected);
        assertThat(HmacSigner.matches("secret", "order_1", "pay_1", expected.toUpperCase()))
                .isTrue();
    }
}

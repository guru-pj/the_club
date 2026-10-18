package com.theclub.eventservice.payment;

import com.theclub.eventservice.model.PaymentProvider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Development stand-in for a real provider. It uses the same signature scheme as Razorpay so the client
 * flow is identical; {@link #sign} backs the dev-only mock-signature endpoint.
 */
public class MockPaymentGateway implements PaymentGateway {

    private final String secret;

    public MockPaymentGateway(String secret) {
        this.secret = secret;
    }

    @Override
    public String createOrder(BigDecimal amount, String currency, String receipt) {
        return "mock_order_" + UUID.randomUUID();
    }

    @Override
    public boolean verify(String providerOrderId, String providerPaymentId, String signature) {
        return HmacSigner.matches(secret, providerOrderId, providerPaymentId, signature);
    }

    public String sign(String providerOrderId, String providerPaymentId) {
        return HmacSigner.sign(secret, providerOrderId, providerPaymentId);
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public String keyId() {
        return "mock";
    }
}

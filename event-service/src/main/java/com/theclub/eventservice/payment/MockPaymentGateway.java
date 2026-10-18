package com.theclub.eventservice.payment;

import com.theclub.eventservice.model.PaymentProvider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Development stand-in for a real provider. It uses the same signature schemes as Razorpay so the client flow
 * and webhooks behave identically; {@link #sign} backs the dev-only mock-signature endpoint.
 * It never reports captures on its own and refunds always succeed.
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

    @Override
    public boolean verifyWebhook(byte[] rawBody, String signature) {
        return HmacSigner.bodyMatches(secret, rawBody, signature);
    }

    @Override
    public Optional<CapturedPayment> findCapturedPayment(String providerOrderId) {
        return Optional.empty();
    }

    @Override
    public String refund(String providerPaymentId, BigDecimal amount, String reason) {
        return "mock_refund_" + UUID.randomUUID();
    }

    public String sign(String providerOrderId, String providerPaymentId) {
        return HmacSigner.sign(secret, providerOrderId, providerPaymentId);
    }

    /** Signs a webhook body the way the provider would (tests and local tooling). */
    public String signWebhook(byte[] rawBody) {
        return HmacSigner.signBody(secret, rawBody);
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

package com.theclub.eventservice.payment;

import com.theclub.eventservice.model.PaymentProvider;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Checkout provider. The client pays against {@link #createOrder}; the payment is settled when either the client
 * sends back a verifiable signature ({@link #verify}), the provider's webhook reports the capture
 * ({@link #verifyWebhook}), or the reconciliation job finds it ({@link #findCapturedPayment}).
 */
public interface PaymentGateway {

    String createOrder(BigDecimal amount, String currency, String receipt);

    /** Checks the signature the checkout widget returned to the client. */
    boolean verify(String providerOrderId, String providerPaymentId, String signature);

    /** Checks a webhook signature over the raw request body. */
    boolean verifyWebhook(byte[] rawBody, String signature);

    /** Asks the provider whether any payment for the order has been captured. */
    Optional<CapturedPayment> findCapturedPayment(String providerOrderId);

    /** Refunds the given amount; returns the provider's refund id. */
    String refund(String providerPaymentId, BigDecimal amount, String reason);

    PaymentProvider provider();

    /** Public key the client SDK needs to open checkout. */
    String keyId();
}

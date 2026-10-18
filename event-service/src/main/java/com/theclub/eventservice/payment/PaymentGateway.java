package com.theclub.eventservice.payment;

import com.theclub.eventservice.model.PaymentProvider;

import java.math.BigDecimal;

/**
 * Checkout provider: the client pays against {@link #createOrder} and sends back the provider's
 * payment id and signature, which {@link #verify} checks before the registration is confirmed.
 */
public interface PaymentGateway {

    String createOrder(BigDecimal amount, String currency, String receipt);

    boolean verify(String providerOrderId, String providerPaymentId, String signature);

    PaymentProvider provider();

    /** Public key the client SDK needs to open checkout. */
    String keyId();
}

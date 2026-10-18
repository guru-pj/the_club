package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentProvider;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutInfo(UUID paymentId, PaymentProvider provider, String providerOrderId, BigDecimal amount,
                           String currency, String providerKeyId) {

    public static CheckoutInfo of(Payment payment, String providerKeyId) {
        return new CheckoutInfo(payment.getId(), payment.getProvider(), payment.getProviderOrderId(),
                payment.getAmount(), payment.getCurrency(), providerKeyId);
    }
}

package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentProvider;
import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.model.RegistrationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID registrationId, UUID eventId, BigDecimal amount, String currency,
                              PaymentProvider provider, String providerOrderId, PaymentStatus status,
                              RegistrationStatus registrationStatus, LocalDateTime createdAt,
                              LocalDateTime refundedAt, String refundReason) {

    public static PaymentResponse of(Payment payment, RegistrationStatus registrationStatus) {
        return new PaymentResponse(payment.getId(), payment.getRegistrationId(), payment.getEventId(),
                payment.getAmount(), payment.getCurrency(), payment.getProvider(), payment.getProviderOrderId(),
                payment.getStatus(), registrationStatus, payment.getCreatedAt(), payment.getRefundedAt(),
                payment.getRefundReason());
    }
}

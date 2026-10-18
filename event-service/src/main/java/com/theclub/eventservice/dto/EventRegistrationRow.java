package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.model.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param paymentId     latest payment for the registration (null for free events).
 * @param paymentStatus status of that payment.
 */
public record EventRegistrationRow(UUID registrationId, UUID userId, String name, RegistrationStatus status,
                                   LocalDateTime registeredAt, LocalDateTime checkedInAt, UUID paymentId,
                                   PaymentStatus paymentStatus) {
}

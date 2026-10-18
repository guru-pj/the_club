package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventRegistrationRow(UUID registrationId, UUID userId, String name, RegistrationStatus status,
                                   LocalDateTime registeredAt, LocalDateTime checkedInAt) {
}

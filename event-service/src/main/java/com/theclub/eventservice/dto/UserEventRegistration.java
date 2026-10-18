package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserEventRegistration(UUID registrationId, RegistrationStatus status, LocalDateTime registeredAt,
                                    LocalDateTime checkedInAt, UUID eventId, UUID clubId, String eventTitle,
                                    LocalDateTime startTime, String location, EventStatus eventStatus) {
}

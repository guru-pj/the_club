package com.theclub.eventservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CheckInResponse(UUID registrationId, UUID userId, String name, LocalDateTime checkedInAt,
                              int pointsAwarded) {
}

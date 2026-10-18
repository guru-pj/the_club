package com.theclub.eventservice.dto;

import java.util.UUID;

public record MemberParticipation(UUID userId, String name, long eventsAttended, long totalPoints, long stars) {
}

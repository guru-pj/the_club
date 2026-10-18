package com.theclub.eventservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * @param clubId present when the stats are scoped to one club.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipationResponse(UUID userId, UUID clubId, long eventsAttended, long totalPoints, long stars) {
}

package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record InternalEventResponse(UUID id, UUID clubId, String title, LocalDateTime startTime, EventStatus status) {

    public static InternalEventResponse from(Event event) {
        return new InternalEventResponse(event.getId(), event.getClubId(), event.getTitle(), event.getStartTime(),
                event.getStatus());
    }
}

package com.theclub.eventservice.repository;

import java.util.UUID;

public record ClubPointsAggregate(UUID clubId, Long points, Long participations) {
}

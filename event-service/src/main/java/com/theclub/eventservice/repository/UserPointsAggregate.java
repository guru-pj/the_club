package com.theclub.eventservice.repository;

import java.util.UUID;

public record UserPointsAggregate(UUID userId, Long points, Long events) {
}

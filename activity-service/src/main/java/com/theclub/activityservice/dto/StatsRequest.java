package com.theclub.activityservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param from inclusive, optional
 * @param to   exclusive, optional
 */
public record StatsRequest(@NotNull @Size(max = 5000) List<UUID> userIds, LocalDateTime from, LocalDateTime to) {
}

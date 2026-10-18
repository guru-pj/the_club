package com.theclub.activityservice.dto;

import com.theclub.activityservice.model.ActivityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Manual activity entry. Any "userId" sent by the client is ignored: activities always belong to the caller.
 *
 * @param distance kilometres
 * @param duration moving time in seconds
 * @param endTime  optional; defaults to startTime + duration
 */
public record ActivityRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull ActivityType type,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") Double distance,
        @NotNull @Min(1) @Max(7 * 24 * 3600) Integer duration,
        @Min(0) @Max(20000) Integer calories,
        @NotNull LocalDateTime startTime,
        LocalDateTime endTime) {
}

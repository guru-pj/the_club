package com.theclub.eventservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Partial update: null fields are left unchanged.
 */
public record UpdateEventRequest(
        @Size(min = 1, max = 150) String title,
        @Size(max = 10_000) String description,
        @Size(max = 255) String location,
        @JsonAlias("date") LocalDateTime startTime,
        LocalDateTime registrationOpensAt,
        LocalDateTime registrationClosesAt,
        @Min(1) Integer capacity,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency,
        @Min(0) @Max(1000) Integer points) {
}

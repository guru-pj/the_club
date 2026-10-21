package com.theclub.eventservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param registrationOpensAt defaults to now when omitted.
 * @param price               0 or omitted means a free event.
 * @param points              points each member earns on check-in (default 10).
 * @param subscribersOnly     only the club's subscribers may register (default: anyone signed in).
 * @param publish             false saves a draft only the owner sees, to review and publish later
 *                            ({@code POST /{id}/publish}); default true.
 */
public record CreateEventRequest(
        @NotNull UUID clubId,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 10_000) String description,
        @Size(max = 255) String location,
        @NotNull @JsonAlias("date") LocalDateTime startTime,
        LocalDateTime registrationOpensAt,
        LocalDateTime registrationClosesAt,
        @Min(1) Integer capacity,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency,
        @Min(0) @Max(1000) Integer points,
        @Size(max = 10_000) String rules,
        Boolean subscribersOnly,
        Boolean publish) {
}

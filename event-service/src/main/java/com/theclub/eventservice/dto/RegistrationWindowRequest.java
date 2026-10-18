package com.theclub.eventservice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * @param registrationClosesAt null keeps registration open until the event starts.
 */
public record RegistrationWindowRequest(@NotNull LocalDateTime registrationOpensAt,
                                        LocalDateTime registrationClosesAt) {
}

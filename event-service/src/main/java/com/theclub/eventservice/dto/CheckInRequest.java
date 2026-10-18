package com.theclub.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param ticketCode the scanned QR payload ("THECLUB-TICKET:&lt;code&gt;") or the bare code.
 */
public record CheckInRequest(@NotBlank @Size(max = 100) String ticketCode) {
}

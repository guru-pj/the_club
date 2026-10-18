package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param qrCodeBase64 PNG of the QR code encoding "THECLUB-TICKET:" + ticketCode.
 */
public record TicketResponse(UUID registrationId, UUID eventId, String eventTitle, LocalDateTime startTime,
                             String location, RegistrationStatus status, String ticketCode, String qrCodeBase64) {
}

package com.theclub.eventservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclub.eventservice.model.RegistrationStatus;

import java.util.UUID;

/**
 * @param payment present only for paid events, holds what the client needs to open checkout.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterResponse(UUID registrationId, RegistrationStatus status, CheckoutInfo payment) {
}

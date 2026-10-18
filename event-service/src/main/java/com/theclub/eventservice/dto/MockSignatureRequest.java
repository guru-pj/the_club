package com.theclub.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MockSignatureRequest(@NotBlank @Size(max = 100) String providerPaymentId) {
}

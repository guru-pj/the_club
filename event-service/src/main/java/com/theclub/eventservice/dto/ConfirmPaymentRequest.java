package com.theclub.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(@NotBlank @Size(max = 100) String providerPaymentId,
                                    @NotBlank @Size(max = 256) String signature) {
}

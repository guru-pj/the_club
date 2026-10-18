package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.VerificationStatus;

import java.util.UUID;

public record ApplyVerificationResponse(UUID requestId, VerificationStatus status) {
}

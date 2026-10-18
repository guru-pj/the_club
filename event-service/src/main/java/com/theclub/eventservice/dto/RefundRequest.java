package com.theclub.eventservice.dto;

import jakarta.validation.constraints.Size;

public record RefundRequest(@Size(max = 255) String reason) {
}

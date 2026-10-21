package com.theclub.clubservice.dto;

import jakarta.validation.constraints.Size;

/** Sends an application back for review after making the changes the admin asked for. */
public record ResubmitVerificationRequest(@Size(max = 2000) String note) {
}

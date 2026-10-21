package com.theclub.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes
        @NotBlank @Size(min = 8, max = 72) String password,
        // true: "I run a club". Owner access still needs an approved club application.
        Boolean clubOwner) {
}

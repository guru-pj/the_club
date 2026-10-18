package com.theclub.authservice.dto;

import java.util.Set;
import java.util.UUID;

public record TokenClaimsResponse(UUID userId, String email, String name, Set<String> roles) {
}

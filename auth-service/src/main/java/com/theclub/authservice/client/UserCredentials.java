package com.theclub.authservice.client;

import java.util.Set;
import java.util.UUID;

public record UserCredentials(UUID id, String name, String email, String passwordHash, Set<String> roles,
                              boolean active, boolean emailVerified) {
}

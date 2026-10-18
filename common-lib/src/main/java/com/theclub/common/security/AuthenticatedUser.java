package com.theclub.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * The caller identity extracted from a validated access token.
 * Inject into controllers with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(UUID id, String email, String name, Set<String> roles) {

    public AuthenticatedUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Roles.ADMIN);
    }
}

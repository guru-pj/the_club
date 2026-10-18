package com.theclub.userservice.dto;

import com.theclub.userservice.model.Role;
import com.theclub.userservice.model.User;

import java.util.Set;
import java.util.UUID;

/**
 * Internal only: lets auth-service verify passwords and embed roles in tokens.
 */
public record UserCredentialsResponse(UUID id, String name, String email, String passwordHash, Set<Role> roles,
                                      boolean active, boolean emailVerified) {

    public static UserCredentialsResponse from(User user) {
        return new UserCredentialsResponse(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash(),
                Set.copyOf(user.getRoles()), user.isActive(), user.isEmailVerified());
    }
}

package com.theclub.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclub.userservice.model.Role;
import com.theclub.userservice.model.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Email and active flag are only included for the user themself or an admin.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(UUID id, String name, String email, Set<Role> roles, String bio, String city,
                           String profilePicUrl, Boolean active, LocalDateTime createdAt) {

    public static UserResponse full(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), Set.copyOf(user.getRoles()),
                user.getBio(), user.getCity(), user.getProfilePicUrl(), user.isActive(), user.getCreatedAt());
    }

    public static UserResponse publicView(User user) {
        return new UserResponse(user.getId(), user.getName(), null, Set.copyOf(user.getRoles()),
                user.getBio(), user.getCity(), user.getProfilePicUrl(), null, user.getCreatedAt());
    }
}

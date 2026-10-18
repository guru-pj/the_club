package com.theclub.userservice.dto;

import com.theclub.userservice.model.User;

import java.util.UUID;

public record UserSummary(UUID id, String name, String profilePicUrl) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getProfilePicUrl());
    }
}

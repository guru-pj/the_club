package com.theclub.userservice.dto;

import com.theclub.userservice.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleRequest(@NotNull Role role) {
}

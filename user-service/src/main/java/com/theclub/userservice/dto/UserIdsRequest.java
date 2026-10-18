package com.theclub.userservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UserIdsRequest(@NotNull @Size(max = 1000) List<UUID> ids) {
}

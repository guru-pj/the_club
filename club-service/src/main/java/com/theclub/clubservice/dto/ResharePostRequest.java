package com.theclub.clubservice.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Exactly one of postId (another club's post) and eventId; comment is the owner's own words above it. */
public record ResharePostRequest(UUID postId, UUID eventId, @Size(max = 2000) String comment) {
}

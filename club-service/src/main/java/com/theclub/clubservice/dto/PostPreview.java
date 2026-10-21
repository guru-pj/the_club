package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.PostType;

import java.time.LocalDateTime;
import java.util.UUID;

/** What a shared link shows before signing in: no comments, no author details beyond the name. */
public record PostPreview(UUID id, UUID clubId, String clubName, String clubLogoUrl, PostType type, String title,
                          String excerpt, String photoUrl, String authorName, LocalDateTime createdAt) {
}

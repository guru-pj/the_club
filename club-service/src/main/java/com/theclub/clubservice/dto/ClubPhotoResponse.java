package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.ClubPhoto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClubPhotoResponse(UUID id, String url, String caption, LocalDateTime createdAt) {

    public static ClubPhotoResponse of(ClubPhoto photo) {
        return new ClubPhotoResponse(photo.getId(), photo.getUrl(), photo.getCaption(), photo.getCreatedAt());
    }
}

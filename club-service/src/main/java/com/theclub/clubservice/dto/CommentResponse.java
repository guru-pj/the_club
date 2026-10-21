package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.PostComment;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommentResponse(UUID id, UUID postId, UUID authorId, String authorName, String authorPhotoUrl,
                              String content, LocalDateTime createdAt) {

    public static CommentResponse of(PostComment c, String authorName, String authorPhotoUrl) {
        return new CommentResponse(c.getId(), c.getPostId(), c.getAuthorId(), authorName, authorPhotoUrl, c.getContent(),
                c.getCreatedAt());
    }
}

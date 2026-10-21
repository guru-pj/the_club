package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.CommentRequest;
import com.theclub.clubservice.dto.CommentResponse;
import com.theclub.clubservice.dto.LikeResponse;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.service.PostInteractionService;
import com.theclub.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Likes and comments on club feed posts. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/posts/{postId}")
public class PostInteractionController {

    private final PostInteractionService interactions;

    public PostInteractionController(PostInteractionService interactions) {
        this.interactions = interactions;
    }

    @PutMapping("/like")
    public LikeResponse like(@PathVariable UUID clubId, @PathVariable UUID postId,
                             @AuthenticationPrincipal AuthenticatedUser caller) {
        return interactions.like(caller, clubId, postId);
    }

    @DeleteMapping("/like")
    public LikeResponse unlike(@PathVariable UUID clubId, @PathVariable UUID postId,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return interactions.unlike(caller, clubId, postId);
    }

    @GetMapping("/comments")
    public PageResponse<CommentResponse> comments(@PathVariable UUID clubId, @PathVariable UUID postId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return interactions.comments(clubId, postId, page, size);
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@PathVariable UUID clubId, @PathVariable UUID postId,
                                   @Valid @RequestBody CommentRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        return interactions.comment(caller, clubId, postId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID clubId, @PathVariable UUID postId, @PathVariable UUID commentId,
                              @AuthenticationPrincipal AuthenticatedUser caller) {
        interactions.deleteComment(caller, clubId, postId, commentId);
    }
}

package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.ReviewPage;
import com.theclub.clubservice.dto.ReviewReplyRequest;
import com.theclub.clubservice.dto.ReviewRequest;
import com.theclub.clubservice.dto.ReviewResponse;
import com.theclub.clubservice.service.ReviewService;
import com.theclub.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reviews and ratings of a club. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public ReviewPage list(@PathVariable UUID clubId, @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @AuthenticationPrincipal AuthenticatedUser caller) {
        return reviews.list(clubId, page, size, caller.id());
    }

    @PutMapping("/me")
    public ReviewResponse upsert(@PathVariable UUID clubId, @Valid @RequestBody ReviewRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        return reviews.upsert(caller, clubId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(@PathVariable UUID clubId, @AuthenticationPrincipal AuthenticatedUser caller) {
        reviews.deleteMine(caller, clubId);
    }

    @PutMapping("/{reviewId}/reply")
    public ReviewResponse reply(@PathVariable UUID clubId, @PathVariable UUID reviewId,
                                @Valid @RequestBody ReviewReplyRequest request,
                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return reviews.reply(caller, clubId, reviewId, request.reply());
    }

    @DeleteMapping("/{reviewId}/reply")
    public ReviewResponse removeReply(@PathVariable UUID clubId, @PathVariable UUID reviewId,
                                      @AuthenticationPrincipal AuthenticatedUser caller) {
        return reviews.reply(caller, clubId, reviewId, null);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID clubId, @PathVariable UUID reviewId,
                       @AuthenticationPrincipal AuthenticatedUser caller) {
        reviews.remove(caller, clubId, reviewId);
    }
}

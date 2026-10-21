package com.theclub.clubservice.dto;

/**
 * A club's reviews with its rating summary.
 *
 * @param canReview whether the caller may write (or edit) a review: a subscriber or event attendee, not the owner
 * @param mine      the caller's own review, if any
 */
public record ReviewPage(ReviewSummary summary, PageResponse<ReviewResponse> reviews, boolean canReview,
                         ReviewResponse mine) {
}

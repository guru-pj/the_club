package com.theclub.clubservice.service;

import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.ReviewPage;
import com.theclub.clubservice.dto.ReviewRequest;
import com.theclub.clubservice.dto.ReviewResponse;
import com.theclub.clubservice.dto.ReviewSummary;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubReview;
import com.theclub.clubservice.model.MembershipRole;
import com.theclub.clubservice.repository.ClubReviewRepository;
import com.theclub.common.observability.BusinessMetrics;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Club reviews and ratings (user journey step 4). Only real participants review: subscribers and people who
 * checked in at one of the club's events, never the owner. One review per person; owners reply, admins moderate.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ClubService clubService;
    private final ClubReviewRepository reviews;
    private final EventClient eventClient;
    private final Clock clock;

    public ReviewService(ClubService clubService, ClubReviewRepository reviews, EventClient eventClient, Clock clock) {
        this.clubService = clubService;
        this.reviews = reviews;
        this.eventClient = eventClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewPage list(UUID clubId, int page, int size, UUID callerId) {
        Club club = clubService.get(clubId);
        Page<ClubReview> result = reviews.findByClubIdOrderByUpdatedAtDesc(clubId, ClubService.pageRequest(page, size));
        List<UUID> authors = new ArrayList<>(result.getContent().stream().map(ClubReview::getUserId).toList());
        ClubReview mine = callerId == null ? null : reviews.findByClubIdAndUserId(clubId, callerId).orElse(null);
        if (mine != null) {
            authors.add(mine.getUserId());
        }
        Map<UUID, String> names = clubService.namesOf(authors);
        return new ReviewPage(ReviewSummary.of(reviews.countByRating(clubId)),
                PageResponse.of(result, r -> ReviewResponse.of(r, names.get(r.getUserId()))),
                callerId != null && mayReview(club, callerId),
                mine == null ? null : ReviewResponse.of(mine, names.get(mine.getUserId())));
    }

    /** Creates the caller's review or updates it. */
    @Transactional
    public ReviewResponse upsert(AuthenticatedUser caller, UUID clubId, ReviewRequest request) {
        Club club = clubService.get(clubId);
        if (club.getOwnerId().equals(caller.id())) {
            throw ApiException.forbidden("You can't review your own club").withCode("OWN_CLUB");
        }
        if (!mayReview(club, caller.id())) {
            throw ApiException.forbidden("Only subscribers and people who attended one of the club's events can review it")
                    .withCode("NOT_ELIGIBLE_TO_REVIEW");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        ClubReview review = reviews.findByClubIdAndUserId(clubId, caller.id())
                .map(existing -> {
                    existing.setRating(request.rating());
                    existing.setComment(comment);
                    return existing;
                })
                .orElseGet(() -> {
                    BusinessMetrics.increment("clubs.reviews");
                    return reviews.save(new ClubReview(clubId, caller.id(), request.rating(), comment));
                });
        reviews.flush();
        return ReviewResponse.of(review, caller.name());
    }

    @Transactional
    public void deleteMine(AuthenticatedUser caller, UUID clubId) {
        clubService.get(clubId);
        reviews.findByClubIdAndUserId(clubId, caller.id()).ifPresent(reviews::delete);
    }

    /** The owner's public answer to a review; an empty reply removes it. */
    @Transactional
    public ReviewResponse reply(AuthenticatedUser caller, UUID clubId, UUID reviewId, String reply) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        ClubReview review = find(clubId, reviewId);
        boolean clear = reply == null || reply.isBlank();
        review.setOwnerReply(clear ? null : reply.strip());
        review.setOwnerReplyAt(clear ? null : LocalDateTime.now(clock));
        return ReviewResponse.of(review, clubService.namesOf(List.of(review.getUserId())).get(review.getUserId()));
    }

    /** Moderation: admins remove abusive reviews. Owners can't, so ratings stay honest. */
    @Transactional
    public void remove(AuthenticatedUser caller, UUID clubId, UUID reviewId) {
        if (!caller.isAdmin()) {
            throw ApiException.forbidden("Only admins can remove other people's reviews");
        }
        reviews.delete(find(clubId, reviewId));
    }

    private ClubReview find(UUID clubId, UUID reviewId) {
        return reviews.findById(reviewId).filter(r -> r.getClubId().equals(clubId))
                .orElseThrow(() -> ApiException.notFound("Review not found"));
    }

    private boolean mayReview(Club club, UUID userId) {
        if (club.getOwnerId().equals(userId)) {
            return false;
        }
        if (clubService.roleOf(club.getId(), userId) == MembershipRole.MEMBER) {
            return true;
        }
        try {
            return eventClient.attendance(club.getId(), userId).attended();
        } catch (FeignException e) {
            log.warn("Couldn't check event attendance for club {}: {}", club.getId(), e.getMessage());
            return false;
        }
    }
}

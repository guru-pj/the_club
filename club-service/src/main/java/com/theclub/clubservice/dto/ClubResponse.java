package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubType;
import com.theclub.clubservice.model.MembershipRole;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param memberCount     subscribers (members, including the owner)
 * @param myRole          the caller's membership, null when not subscribed
 * @param following       whether the caller follows the club
 * @param ratingAverage   mean review rating (1–5), null without reviews
 * @param distanceKm      distance from the point a nearby search was made from; null elsewhere
 */
public record ClubResponse(UUID id, String name, String description, String city, ClubType clubType, String logoUrl,
                           UUID ownerId, long memberCount, MembershipRole myRole, LocalDateTime createdAt,
                           Double latitude, Double longitude, String address,
                           boolean subscriptionsEnabled, String subscriptionBenefits,
                           long followerCount, boolean following, Double ratingAverage, long ratingCount,
                           Double distanceKm) {

    /** Per-club numbers and the caller's relation to the club. */
    public record Stats(long memberCount, MembershipRole myRole, long followerCount, boolean following,
                        Double ratingAverage, long ratingCount) {
    }

    public static ClubResponse of(Club club, Stats stats, Double distanceKm) {
        return new ClubResponse(club.getId(), club.getName(), club.getDescription(), club.getCity(),
                club.getClubType(), club.getLogoUrl(), club.getOwnerId(), stats.memberCount(), stats.myRole(),
                club.getCreatedAt(), club.getLatitude(), club.getLongitude(), club.getAddress(),
                club.isSubscriptionsEnabled(), club.getSubscriptionBenefits(), stats.followerCount(),
                stats.following(), stats.ratingAverage(), stats.ratingCount(), distanceKm);
    }
}

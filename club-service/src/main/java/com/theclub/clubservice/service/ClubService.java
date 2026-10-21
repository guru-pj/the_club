package com.theclub.clubservice.service;

import com.theclub.common.observability.BusinessMetrics;
import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.ClubResponse;
import com.theclub.clubservice.dto.ReviewSummary;
import com.theclub.clubservice.dto.CreateClubRequest;
import com.theclub.clubservice.dto.MembershipResponse;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.UpdateClubRequest;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubFollow;
import com.theclub.clubservice.model.ClubMembership;
import com.theclub.clubservice.model.MembershipRole;
import com.theclub.clubservice.repository.ClubFollowRepository;
import com.theclub.clubservice.repository.ClubMembershipRepository;
import com.theclub.clubservice.repository.ClubReviewRepository;
import com.theclub.clubservice.repository.ClubRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClubService {

    private static final Logger log = LoggerFactory.getLogger(ClubService.class);
    static final int MAX_PAGE_SIZE = 100;

    private final ClubRepository clubs;
    private final ClubMembershipRepository memberships;
    private final ClubFollowRepository follows;
    private final ClubReviewRepository reviews;
    private final UserClient userClient;

    public ClubService(ClubRepository clubs, ClubMembershipRepository memberships, ClubFollowRepository follows,
                       ClubReviewRepository reviews, UserClient userClient) {
        this.clubs = clubs;
        this.memberships = memberships;
        this.follows = follows;
        this.reviews = reviews;
        this.userClient = userClient;
    }

    /** Result of a follow: whether it was new. */
    public record FollowResult(boolean created) {
    }

    public record JoinResult(ClubMembership membership, boolean created) {
    }

    @Transactional
    public Club create(AuthenticatedUser caller, CreateClubRequest request) {
        if (!caller.hasRole(Roles.CLUB_OWNER) && !caller.isAdmin()) {
            throw ApiException.forbidden("Only verified club owners can create clubs");
        }
        return createFor(caller.id(), request);
    }

    /**
     * Creates a club owned by {@code ownerId} without checking roles (used after owner verification approval).
     */
    @Transactional
    public Club createFor(UUID ownerId, CreateClubRequest request) {
        String name = request.name().trim();
        if (clubs.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A club with this name already exists");
        }
        Club club = new Club(name, request.description(), request.city(), request.clubType(), request.logoUrl(), ownerId);
        applyLocation(club, request.latitude(), request.longitude(), false);
        club.setAddress(blankToNull(request.address()));
        if (request.subscriptionsEnabled() != null) {
            club.setSubscriptionsEnabled(request.subscriptionsEnabled());
        }
        club.setSubscriptionBenefits(blankToNull(request.subscriptionBenefits()));
        club = clubs.save(club);
        memberships.save(new ClubMembership(club.getId(), ownerId, MembershipRole.OWNER));
        BusinessMetrics.increment("clubs.created");
        return club;
    }

    @Transactional(readOnly = true)
    public boolean nameTaken(String name) {
        return clubs.existsByNameIgnoreCase(name.trim());
    }

    @Transactional(readOnly = true)
    public Club get(UUID clubId) {
        return clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found"));
    }

    @Transactional(readOnly = true)
    public ClubResponse describe(Club club, UUID callerId) {
        return describe(club, callerId, null);
    }

    @Transactional(readOnly = true)
    public ClubResponse describe(Club club, UUID callerId, Double distanceKm) {
        MembershipRole myRole = callerId == null ? null
                : memberships.findByClubIdAndUserId(club.getId(), callerId).map(ClubMembership::getRole).orElse(null);
        boolean following = callerId != null && follows.existsByClubIdAndUserId(club.getId(), callerId);
        ReviewSummary rating = ReviewSummary.of(reviews.countByRating(club.getId()));
        return ClubResponse.of(club, new ClubResponse.Stats(memberships.countByClubId(club.getId()), myRole,
                follows.countByClubId(club.getId()), following, rating.average(), rating.count()), distanceKm);
    }

    @Transactional(readOnly = true)
    public ClubResponse describe(UUID clubId, UUID callerId) {
        return describe(get(clubId), callerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClubResponse> search(String q, String city, int page, int size, UUID callerId) {
        Page<Club> result = clubs.search(blankToNull(q), blankToNull(city), pageRequest(page, size));
        return PageResponse.of(result, club -> describe(club, callerId));
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> ownedBy(UUID ownerId, UUID callerId) {
        return clubs.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(c -> describe(c, callerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> joinedBy(UUID userId, UUID callerId) {
        return clubs.findJoinedBy(userId).stream().map(c -> describe(c, callerId)).toList();
    }

    @Transactional
    public Club update(AuthenticatedUser caller, UUID clubId, UpdateClubRequest request) {
        Club club = get(clubId);
        requireOwnerOrAdmin(club, caller);
        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(club.getName()) && clubs.existsByNameIgnoreCase(name)) {
                throw ApiException.conflict("A club with this name already exists");
            }
            club.setName(name);
        }
        if (request.description() != null) {
            club.setDescription(request.description());
        }
        if (request.city() != null) {
            club.setCity(request.city());
        }
        if (request.clubType() != null) {
            club.setClubType(request.clubType());
        }
        if (request.logoUrl() != null) {
            club.setLogoUrl(request.logoUrl());
        }
        applyLocation(club, request.latitude(), request.longitude(), Boolean.TRUE.equals(request.clearLocation()));
        if (request.address() != null) {
            club.setAddress(request.address().isBlank() ? null : request.address());
        }
        if (request.subscriptionsEnabled() != null) {
            club.setSubscriptionsEnabled(request.subscriptionsEnabled());
        }
        if (request.subscriptionBenefits() != null) {
            club.setSubscriptionBenefits(request.subscriptionBenefits().isBlank() ? null : request.subscriptionBenefits());
        }
        return club;
    }

    private static void applyLocation(Club club, Double latitude, Double longitude, boolean clear) {
        if (clear) {
            club.setLatitude(null);
            club.setLongitude(null);
        } else if (latitude != null || longitude != null) {
            if (latitude == null || longitude == null) {
                throw ApiException.badRequest("Send both latitude and longitude").withCode("INCOMPLETE_LOCATION");
            }
            club.setLatitude(latitude);
            club.setLongitude(longitude);
        }
    }

    /** Subscribing (membership); only where the club offers subscriptions. */
    @Transactional
    public JoinResult join(UUID userId, UUID clubId) {
        Club club = get(clubId);
        return memberships.findByClubIdAndUserId(clubId, userId)
                .map(existing -> new JoinResult(existing, false))
                .orElseGet(() -> {
                    if (!club.isSubscriptionsEnabled()) {
                        throw ApiException.forbidden("This club doesn't offer subscriptions; you can follow it instead")
                                .withCode("SUBSCRIPTIONS_DISABLED");
                    }
                    BusinessMetrics.increment("clubs.joins");
                    return new JoinResult(memberships.save(new ClubMembership(clubId, userId, MembershipRole.MEMBER)), true);
                });
    }

    @Transactional
    public void leave(UUID userId, UUID clubId) {
        get(clubId);
        ClubMembership membership = memberships.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> ApiException.notFound("You are not a member of this club"));
        if (membership.getRole() == MembershipRole.OWNER) {
            throw ApiException.badRequest("The club owner cannot leave the club");
        }
        memberships.delete(membership);
    }

    @Transactional
    public void removeMember(AuthenticatedUser caller, UUID clubId, UUID userId) {
        Club club = get(clubId);
        requireOwnerOrAdmin(club, caller);
        ClubMembership membership = memberships.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (membership.getRole() == MembershipRole.OWNER) {
            throw ApiException.badRequest("The club owner cannot be removed");
        }
        memberships.delete(membership);
    }

    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> members(UUID clubId, int page, int size) {
        get(clubId);
        Page<ClubMembership> result = memberships.findByClubIdOrderByJoinedAtAsc(clubId, pageRequest(page, size));
        Map<UUID, String> names = namesOf(result.getContent().stream().map(ClubMembership::getUserId).toList());
        return PageResponse.of(result, m -> MembershipResponse.of(m, names.get(m.getUserId())));
    }

    @Transactional(readOnly = true)
    public List<UUID> memberIds(UUID clubId) {
        return memberships.findUserIdsByClubId(clubId);
    }

    @Transactional
    public FollowResult follow(UUID userId, UUID clubId) {
        get(clubId);
        if (follows.existsByClubIdAndUserId(clubId, userId)) {
            return new FollowResult(false);
        }
        follows.save(new ClubFollow(clubId, userId));
        BusinessMetrics.increment("clubs.follows");
        return new FollowResult(true);
    }

    @Transactional
    public void unfollow(UUID userId, UUID clubId) {
        get(clubId);
        follows.findByClubIdAndUserId(clubId, userId).ifPresent(follows::delete);
    }

    /** Who hears about the club's posts and events: subscribers and followers, each once. */
    @Transactional(readOnly = true)
    public Set<UUID> audienceIds(UUID clubId) {
        Set<UUID> audience = new LinkedHashSet<>(memberships.findUserIdsByClubId(clubId));
        audience.addAll(follows.findUserIdsByClubId(clubId));
        return audience;
    }

    @Transactional(readOnly = true)
    public List<UUID> followedClubIds(UUID userId) {
        return follows.findClubIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> subscribedClubIds(UUID userId) {
        return clubs.findJoinedBy(userId).stream().map(Club::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> followedBy(UUID userId) {
        return clubs.findAllById(followedClubIds(userId)).stream()
                .sorted(Comparator.comparing(Club::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> describe(c, userId)).toList();
    }

    @Transactional(readOnly = true)
    public MembershipRole roleOf(UUID clubId, UUID userId) {
        return memberships.findByClubIdAndUserId(clubId, userId).map(ClubMembership::getRole).orElse(null);
    }

    @Transactional(readOnly = true)
    public long memberCount(UUID clubId) {
        return memberships.countByClubId(clubId);
    }

    @Transactional(readOnly = true)
    public List<Club> getAll(Collection<UUID> ids) {
        return clubs.findAllById(ids);
    }

    /**
     * Looks up display names; returns an empty map when user-service is unavailable.
     */
    public Map<UUID, String> namesOf(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userClient.summaries(new UserClient.IdsRequest(List.copyOf(userIds))).stream()
                    .filter(s -> s.name() != null)
                    .collect(Collectors.toMap(UserClient.UserSummary::id, UserClient.UserSummary::name, (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("Could not load user names: {}", e.getMessage());
            return Map.of();
        }
    }

    public static boolean isOwnerOrAdmin(Club club, AuthenticatedUser caller) {
        return club.getOwnerId().equals(caller.id()) || caller.isAdmin();
    }

    public static void requireOwnerOrAdmin(Club club, AuthenticatedUser caller) {
        if (!isOwnerOrAdmin(club, caller)) {
            throw ApiException.forbidden("Only the club owner can do this");
        }
    }

    static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

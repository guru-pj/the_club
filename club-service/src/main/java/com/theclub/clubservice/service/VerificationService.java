package com.theclub.clubservice.service;

import com.theclub.common.observability.BusinessMetrics;
import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.ApplyVerificationRequest;
import com.theclub.clubservice.dto.ApproveVerificationRequest;
import com.theclub.clubservice.dto.CreateClubRequest;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.UpdateClubRequest;
import com.theclub.clubservice.dto.UploadProofRequest;
import com.theclub.clubservice.dto.VerificationResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.VerificationAudit;
import com.theclub.clubservice.model.VerificationRequest;
import com.theclub.clubservice.model.VerificationStatus;
import com.theclub.clubservice.repository.VerificationAuditRepository;
import com.theclub.clubservice.repository.VerificationRequestRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Club owner verification: a user applies, an admin approves / rejects / asks for more information.
 * Approval grants CLUB_OWNER in user-service and can create the club straight away.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final String NOTIFICATION_TYPE = "OWNER_VERIFICATION";
    private static final Set<VerificationStatus> REVIEWABLE =
            EnumSet.of(VerificationStatus.PENDING, VerificationStatus.UNDER_REVIEW);

    private final VerificationRequestRepository requests;
    private final VerificationAuditRepository audits;
    private final ClubService clubService;
    private final UserClient userClient;
    private final NotificationPublisher notifications;
    private final Clock clock;

    public VerificationService(VerificationRequestRepository requests, VerificationAuditRepository audits,
                               ClubService clubService, UserClient userClient, NotificationPublisher notifications,
                               Clock clock) {
        this.requests = requests;
        this.audits = audits;
        this.clubService = clubService;
        this.userClient = userClient;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public VerificationRequest apply(AuthenticatedUser caller, ApplyVerificationRequest body) {
        if (caller.hasRole(Roles.CLUB_OWNER)) {
            throw ApiException.conflict("You are already a verified club owner");
        }
        if (requests.existsByUserIdAndStatusIn(caller.id(), VerificationStatus.OPEN)) {
            throw ApiException.conflict("You already have an open verification request");
        }
        VerificationRequest request = new VerificationRequest(caller.id(), body.clubName().trim());
        request.setClubType(body.clubType());
        request.setCity(body.city());
        if (body.socialLinks() != null) {
            request.getSocialLinks().putAll(body.socialLinks());
        }
        if (body.proofFiles() != null) {
            request.getProofFiles().addAll(body.proofFiles());
        }
        request.setDescription(body.description());
        request.setContactPhone(body.contactPhone());
        VerificationRequest saved = requests.save(request);
        audit(saved, caller.id(), "APPLIED", null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<VerificationRequest> mine(UUID userId) {
        return requests.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public VerificationRequest getVisibleTo(AuthenticatedUser caller, UUID id) {
        VerificationRequest request = get(id);
        if (!request.getUserId().equals(caller.id()) && !caller.isAdmin()) {
            throw ApiException.forbidden("You cannot view this request");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public PageResponse<VerificationResponse> list(VerificationStatus status, int page, int size) {
        var pageable = ClubService.pageRequest(page, size);
        var result = status == null
                ? requests.findAllByOrderByCreatedAtAsc(pageable)
                : requests.findByStatusOrderByCreatedAtAsc(status, pageable);
        return PageResponse.of(result, VerificationResponse::of);
    }

    @Transactional(readOnly = true)
    public List<VerificationAudit> history(UUID id) {
        return audits.findByRequestIdOrderByCreatedAtAsc(id);
    }

    /**
     * Any failure (user-service down, duplicate club name) throws and rolls the approval back.
     */
    @Transactional
    public VerificationResponse approve(AuthenticatedUser admin, UUID id, ApproveVerificationRequest body) {
        VerificationRequest request = get(id);
        requireStatus(request, REVIEWABLE, "approved");

        CreateClubRequest clubRequest = body != null && body.createClub() ? clubRequestFor(request, body.clubPayload()) : null;
        // Check before granting the role so a name clash does not leave a half-applied approval behind.
        if (clubRequest != null && clubService.nameTaken(clubRequest.name())) {
            throw ApiException.conflict("A club with this name already exists; pass a different clubPayload.name");
        }
        markReviewed(request, admin, VerificationStatus.APPROVED);
        audit(request, admin.id(), "APPROVED", null);

        try {
            userClient.addRole(request.getUserId(), new UserClient.RoleRequest(Roles.CLUB_OWNER));
        } catch (RuntimeException e) {
            log.warn("Could not grant CLUB_OWNER to {}: {}", request.getUserId(), e.getMessage());
            throw ApiException.badGateway("Could not update the user's role; the request was not approved");
        }

        UUID clubId = null;
        if (clubRequest != null) {
            Club club = clubService.createFor(request.getUserId(), clubRequest);
            clubId = club.getId();
            audit(request, admin.id(), "CLUB_CREATED", clubId.toString());
        }
        notifications.notifyAfterCommit(request.getUserId(), NOTIFICATION_TYPE,
                "Your club owner application for \"" + request.getClubName() + "\" was approved. "
                        + "Sign in again (or refresh your session) to start managing your club.");
        return VerificationResponse.of(request, clubId);
    }

    @Transactional
    public VerificationRequest reject(AuthenticatedUser admin, UUID id, String reason) {
        VerificationRequest request = get(id);
        requireStatus(request, VerificationStatus.OPEN, "rejected");
        markReviewed(request, admin, VerificationStatus.REJECTED);
        request.setAdminNotes(reason);
        audit(request, admin.id(), "REJECTED", reason);
        notifications.notifyAfterCommit(request.getUserId(), NOTIFICATION_TYPE,
                "Your club owner application for \"" + request.getClubName() + "\" was rejected: " + reason
                        + ". You can apply again.");
        return request;
    }

    @Transactional
    public VerificationRequest requestInfo(AuthenticatedUser admin, UUID id, String message) {
        VerificationRequest request = get(id);
        requireStatus(request, REVIEWABLE, "sent back for more information");
        markReviewed(request, admin, VerificationStatus.NEEDS_INFO);
        request.setAdminNotes(message);
        audit(request, admin.id(), "INFO_REQUESTED", message);
        notifications.notifyAfterCommit(request.getUserId(), NOTIFICATION_TYPE,
                "More information is needed for your club owner application: " + message);
        return request;
    }

    @Transactional
    public VerificationRequest uploadProof(AuthenticatedUser caller, UUID id, UploadProofRequest body) {
        VerificationRequest request = get(id);
        if (!request.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("You can only add proof to your own request");
        }
        requireStatus(request, VerificationStatus.OPEN, "updated");
        request.getProofFiles().addAll(body.proofFiles());
        if (request.getStatus() == VerificationStatus.NEEDS_INFO) {
            request.setStatus(VerificationStatus.UNDER_REVIEW);
        }
        audit(request, caller.id(), "PROOF_UPLOADED", body.note());
        return request;
    }

    private VerificationRequest get(UUID id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Verification request not found"));
    }

    private void markReviewed(VerificationRequest request, AuthenticatedUser admin, VerificationStatus status) {
        request.setStatus(status);
        request.setReviewedBy(admin.id());
        request.setReviewedAt(LocalDateTime.now(clock));
    }

    private void audit(VerificationRequest request, UUID actorId, String action, String note) {
        BusinessMetrics.increment("clubs.owner_verifications", "action", action.toLowerCase(java.util.Locale.ROOT));
        audits.save(new VerificationAudit(request.getId(), actorId, action, note));
    }

    private static void requireStatus(VerificationRequest request, Set<VerificationStatus> allowed, String action) {
        if (!allowed.contains(request.getStatus())) {
            throw ApiException.conflict("A request in status " + request.getStatus() + " cannot be " + action);
        }
    }

    private static CreateClubRequest clubRequestFor(VerificationRequest request, UpdateClubRequest payload) {
        UpdateClubRequest p = payload == null ? new UpdateClubRequest(null, null, null, null, null) : payload;
        return new CreateClubRequest(
                p.name() != null ? p.name() : request.getClubName(),
                p.description() != null ? p.description() : request.getDescription(),
                p.city() != null ? p.city() : request.getCity(),
                p.clubType() != null ? p.clubType() : request.getClubType(),
                p.logoUrl());
    }
}

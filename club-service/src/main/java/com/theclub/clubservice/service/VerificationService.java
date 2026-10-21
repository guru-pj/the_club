package com.theclub.clubservice.service;

import com.theclub.common.media.MediaStore;
import com.theclub.common.media.StoredDocument;
import com.theclub.common.observability.BusinessMetrics;
import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.ApplyVerificationRequest;
import com.theclub.clubservice.dto.ApproveVerificationRequest;
import com.theclub.clubservice.dto.CreateClubRequest;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.ResubmitVerificationRequest;
import com.theclub.clubservice.dto.UpdateClubRequest;
import com.theclub.clubservice.dto.UploadProofRequest;
import com.theclub.clubservice.dto.VerificationResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.VerificationAudit;
import com.theclub.clubservice.model.VerificationDocument;
import com.theclub.clubservice.model.VerificationRequest;
import com.theclub.clubservice.model.VerificationStatus;
import com.theclub.clubservice.repository.VerificationAuditRepository;
import com.theclub.clubservice.repository.VerificationDocumentRepository;
import com.theclub.clubservice.repository.VerificationRequestRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Club ownership verification, once per club: a user applies for a club with its details and proof, an admin
 * approves, rejects or asks for changes, and the applicant updates and resubmits. Approval creates the club, links it
 * to the applicant and grants CLUB_OWNER; approving one club never covers another.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final String NOTIFICATION_TYPE = "OWNER_VERIFICATION";
    private static final Set<VerificationStatus> REVIEWABLE =
            EnumSet.of(VerificationStatus.PENDING, VerificationStatus.UNDER_REVIEW);
    /** Statuses in which the applicant may still change the application. */
    private static final Set<VerificationStatus> EDITABLE = EnumSet.of(VerificationStatus.PENDING, VerificationStatus.NEEDS_INFO);
    static final int MAX_OPEN_APPLICATIONS = 3;
    static final int MAX_DOCUMENTS = 10;

    private final VerificationRequestRepository requests;
    private final VerificationAuditRepository audits;
    private final VerificationDocumentRepository documents;
    private final ClubService clubService;
    private final UserClient userClient;
    private final NotificationPublisher notifications;
    private final MediaStore media;
    private final Clock clock;

    public VerificationService(VerificationRequestRepository requests, VerificationAuditRepository audits,
                               VerificationDocumentRepository documents, ClubService clubService, UserClient userClient,
                               NotificationPublisher notifications, MediaStore media, Clock clock) {
        this.requests = requests;
        this.audits = audits;
        this.documents = documents;
        this.clubService = clubService;
        this.userClient = userClient;
        this.notifications = notifications;
        this.media = media;
        this.clock = clock;
    }

    /** A downloaded proof document. */
    public record DocumentFile(String fileName, String contentType, byte[] bytes) {
    }

    @Transactional
    public VerificationRequest apply(AuthenticatedUser caller, ApplyVerificationRequest body) {
        String clubName = body.clubName().trim();
        if (requests.existsByUserIdAndClubNameIgnoreCaseAndStatusIn(caller.id(), clubName, VerificationStatus.OPEN)) {
            throw ApiException.conflict("You already have an open application for this club").withCode("DUPLICATE_APPLICATION");
        }
        if (requests.countByUserIdAndStatusIn(caller.id(), VerificationStatus.OPEN) >= MAX_OPEN_APPLICATIONS) {
            throw ApiException.conflict("You can have at most " + MAX_OPEN_APPLICATIONS
                    + " applications waiting for review").withCode("TOO_MANY_APPLICATIONS");
        }
        requireNewClub(clubName);
        VerificationRequest request = new VerificationRequest(caller.id(), clubName);
        applyDetails(request, body);
        if (body.proofFiles() != null) {
            request.getProofFiles().addAll(body.proofFiles());
        }
        VerificationRequest saved = requests.save(request);
        audit(saved, caller.id(), "APPLIED", null);
        return saved;
    }

    /** The applicant corrects their application while it waits for review or after an admin asked for changes. */
    @Transactional
    public VerificationRequest update(AuthenticatedUser caller, UUID id, ApplyVerificationRequest body) {
        VerificationRequest request = ownRequest(caller, id);
        requireStatus(request, EDITABLE, "changed");
        String clubName = body.clubName().trim();
        if (!clubName.equalsIgnoreCase(request.getClubName())) {
            requireNewClub(clubName);
        }
        request.setClubName(clubName);
        applyDetails(request, body);
        if (body.proofFiles() != null) {
            request.getProofFiles().clear();
            request.getProofFiles().addAll(body.proofFiles());
        }
        audit(request, caller.id(), "UPDATED", null);
        return request;
    }

    /** After making the requested changes: back to the review queue. */
    @Transactional
    public VerificationRequest resubmit(AuthenticatedUser caller, UUID id, ResubmitVerificationRequest body) {
        VerificationRequest request = ownRequest(caller, id);
        requireStatus(request, EnumSet.of(VerificationStatus.NEEDS_INFO), "resubmitted");
        request.setStatus(VerificationStatus.PENDING);
        audit(request, caller.id(), "RESUBMITTED", body == null ? null : blankToNull(body.note()));
        return request;
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
    public VerificationResponse describe(VerificationRequest request) {
        return VerificationResponse.of(request, documents.findByRequestIdOrderByCreatedAtAsc(request.getId()));
    }

    @Transactional(readOnly = true)
    public List<VerificationResponse> describeMine(UUID userId) {
        return mine(userId).stream().map(this::describe).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<VerificationResponse> list(VerificationStatus status, int page, int size) {
        var pageable = ClubService.pageRequest(page, size);
        var result = status == null
                ? requests.findAllByOrderByCreatedAtAsc(pageable)
                : requests.findByStatusOrderByCreatedAtAsc(status, pageable);
        return PageResponse.of(result, this::describe);
    }

    @Transactional(readOnly = true)
    public List<VerificationAudit> history(UUID id) {
        return audits.findByRequestIdOrderByCreatedAtAsc(id);
    }

    // ---- Documents ----------------------------------------------------------------------------------------------

    @Transactional
    public VerificationDocument addDocument(AuthenticatedUser caller, UUID id, MultipartFile file) {
        VerificationRequest request = ownRequest(caller, id);
        requireStatus(request, VerificationStatus.OPEN, "changed");
        if (documents.countByRequestId(id) >= MAX_DOCUMENTS) {
            throw ApiException.conflict("An application can have at most " + MAX_DOCUMENTS + " documents; remove one first")
                    .withCode("TOO_MANY_DOCUMENTS");
        }
        StoredDocument stored = media.storePrivate("verification/" + id, file);
        VerificationDocument document = documents.save(new VerificationDocument(id, safeFileName(file.getOriginalFilename(),
                stored.contentType()), stored.contentType(), stored.size(), stored.key(), caller.id(), LocalDateTime.now(clock)));
        audit(request, caller.id(), "DOCUMENT_ADDED", document.getFileName());
        return document;
    }

    /** The applicant and admins can download a document. */
    @Transactional(readOnly = true)
    public DocumentFile document(AuthenticatedUser caller, UUID id, UUID documentId) {
        getVisibleTo(caller, id);
        VerificationDocument document = documents.findById(documentId).filter(d -> d.getRequestId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Document not found"));
        byte[] bytes = media.readPrivate(document.getStorageKey())
                .orElseThrow(() -> ApiException.notFound("The file is no longer available"));
        return new DocumentFile(document.getFileName(), document.getContentType(), bytes);
    }

    @Transactional
    public void removeDocument(AuthenticatedUser caller, UUID id, UUID documentId) {
        VerificationRequest request = ownRequest(caller, id);
        requireStatus(request, VerificationStatus.OPEN, "changed");
        VerificationDocument document = documents.findById(documentId).filter(d -> d.getRequestId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Document not found"));
        documents.delete(document);
        media.deletePrivate(document.getStorageKey());
        audit(request, caller.id(), "DOCUMENT_REMOVED", document.getFileName());
    }

    // ---- Admin decisions ----------------------------------------------------------------------------------------

    /**
     * Creates the club from the application (with the admin's corrections, the name being the official verified one),
     * links it to the applicant and makes sure they have CLUB_OWNER. Any failure rolls the approval back.
     */
    @Transactional
    public VerificationResponse approve(AuthenticatedUser admin, UUID id, ApproveVerificationRequest body) {
        VerificationRequest request = get(id);
        requireStatus(request, REVIEWABLE, "approved");

        CreateClubRequest clubRequest = clubRequestFor(request, body == null ? null : body.clubPayload());
        // Check before granting the role so a name clash does not leave a half-applied approval behind.
        if (clubService.nameTaken(clubRequest.name())) {
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

        Club club = clubService.createFor(request.getUserId(), clubRequest);
        request.setClubId(club.getId());
        audit(request, admin.id(), "CLUB_CREATED", club.getId().toString());
        notifications.notifyAfterCommit(request.getUserId(), NOTIFICATION_TYPE,
                "Your application for \"" + club.getName() + "\" was approved and the club is now linked to your account. "
                        + "Complete its profile to open it to members.");
        return describe(request);
    }

    @Transactional
    public VerificationRequest reject(AuthenticatedUser admin, UUID id, String reason) {
        VerificationRequest request = get(id);
        requireStatus(request, VerificationStatus.OPEN, "rejected");
        markReviewed(request, admin, VerificationStatus.REJECTED);
        request.setAdminNotes(reason);
        audit(request, admin.id(), "REJECTED", reason);
        notifications.notifyAfterCommit(request.getUserId(), NOTIFICATION_TYPE,
                "Your application for \"" + request.getClubName() + "\" was rejected: " + reason + ". You can apply again.");
        return request;
    }

    /** "Changes required": the applicant is told what to correct and can update and resubmit. */
    @Transactional
    public VerificationRequest requestInfo(AuthenticatedUser admin, UUID id, String message) {
        VerificationRequest request = get(id);
        requireStatus(request, REVIEWABLE, "sent back for changes");
        markReviewed(request, admin, VerificationStatus.NEEDS_INFO);
        request.setAdminNotes(message);
        audit(request, admin.id(), "INFO_REQUESTED", message);
        notifications.notifyAfterCommit(request.getUserId(), NOTIFICATION_TYPE,
                "Changes are needed for your application for \"" + request.getClubName() + "\": " + message);
        return request;
    }

    /** Adds proof links; after a request for changes this also sends the application back for review. */
    @Transactional
    public VerificationRequest uploadProof(AuthenticatedUser caller, UUID id, UploadProofRequest body) {
        VerificationRequest request = ownRequest(caller, id);
        requireStatus(request, VerificationStatus.OPEN, "updated");
        request.getProofFiles().addAll(body.proofFiles());
        if (request.getStatus() == VerificationStatus.NEEDS_INFO) {
            request.setStatus(VerificationStatus.PENDING);
        }
        audit(request, caller.id(), "PROOF_UPLOADED", body.note());
        return request;
    }

    // ---- Helpers ------------------------------------------------------------------------------------------------

    private void requireNewClub(String clubName) {
        if (clubService.nameTaken(clubName)) {
            throw ApiException.conflict("A club with this name is already on The Club. If you run it, contact the admin team.")
                    .withCode("CLUB_EXISTS");
        }
    }

    private static void applyDetails(VerificationRequest request, ApplyVerificationRequest body) {
        if (blankToNull(body.contactEmail()) == null && blankToNull(body.contactPhone()) == null) {
            throw ApiException.badRequest("Give the club's contact email or phone").withCode("CONTACT_REQUIRED");
        }
        if ((body.latitude() == null) != (body.longitude() == null)) {
            throw ApiException.badRequest("Send both latitude and longitude").withCode("INCOMPLETE_LOCATION");
        }
        request.setClubType(body.clubType());
        request.setCity(blankToNull(body.city()));
        request.setDescription(blankToNull(body.description()));
        request.setContactPhone(blankToNull(body.contactPhone()));
        request.setContactEmail(blankToNull(body.contactEmail()));
        request.setAddress(blankToNull(body.address()));
        request.setLatitude(body.latitude());
        request.setLongitude(body.longitude());
        request.setWebsite(blankToNull(body.website()));
        request.getSocialLinks().clear();
        if (body.socialLinks() != null) {
            body.socialLinks().forEach((name, url) -> {
                if (url != null && !url.isBlank()) {
                    request.getSocialLinks().put(name, url.strip());
                }
            });
        }
    }

    private VerificationRequest ownRequest(AuthenticatedUser caller, UUID id) {
        VerificationRequest request = get(id);
        if (!request.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("You can only change your own application");
        }
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
        BusinessMetrics.increment("clubs.owner_verifications", "action", action.toLowerCase(Locale.ROOT));
        audits.save(new VerificationAudit(request.getId(), actorId, action, note));
    }

    private static void requireStatus(VerificationRequest request, Set<VerificationStatus> allowed, String action) {
        if (!allowed.contains(request.getStatus())) {
            throw ApiException.conflict("A request in status " + request.getStatus() + " cannot be " + action);
        }
    }

    /** Keeps the name readable in a download header: letters, digits, dot, dash, underscore and spaces. */
    static String safeFileName(String original, String contentType) {
        String name = original == null ? "" : original.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        if (name.isEmpty() || name.chars().allMatch(c -> c == '_' || c == '.')) {
            name = "document." + com.theclub.common.media.DocumentType.extension(contentType);
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static CreateClubRequest clubRequestFor(VerificationRequest request, UpdateClubRequest payload) {
        UpdateClubRequest p = payload == null
                ? new UpdateClubRequest(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null) : payload;
        return new CreateClubRequest(
                p.name() != null ? p.name().trim() : request.getClubName(),
                p.description() != null ? p.description() : request.getDescription(),
                p.city() != null ? p.city() : request.getCity(),
                p.clubType() != null ? p.clubType() : request.getClubType(),
                p.logoUrl(),
                p.latitude() != null ? p.latitude() : request.getLatitude(),
                p.longitude() != null ? p.longitude() : request.getLongitude(),
                p.address() != null ? p.address() : request.getAddress(),
                p.subscriptionsEnabled(), p.subscriptionBenefits(),
                p.contactEmail() != null ? p.contactEmail() : request.getContactEmail(),
                p.contactPhone() != null ? p.contactPhone() : request.getContactPhone(),
                p.website() != null ? p.website() : request.getWebsite(),
                p.socialLinks() != null ? p.socialLinks() : Map.copyOf(request.getSocialLinks()),
                p.rules(), request.getUserId());
    }
}

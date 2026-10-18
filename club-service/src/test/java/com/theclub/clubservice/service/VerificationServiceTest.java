package com.theclub.clubservice.service;

import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.ApplyVerificationRequest;
import com.theclub.clubservice.dto.ApproveVerificationRequest;
import com.theclub.clubservice.dto.CreateClubRequest;
import com.theclub.clubservice.dto.UploadProofRequest;
import com.theclub.clubservice.dto.VerificationResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubType;
import com.theclub.clubservice.model.VerificationRequest;
import com.theclub.clubservice.model.VerificationStatus;
import com.theclub.clubservice.repository.VerificationAuditRepository;
import com.theclub.clubservice.repository.VerificationRequestRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationServiceTest {

    @Mock
    VerificationRequestRepository requests;
    @Mock
    VerificationAuditRepository audits;
    @Mock
    ClubService clubService;
    @Mock
    UserClient userClient;
    @Mock
    NotificationPublisher notifications;
    VerificationService service;

    private final AuthenticatedUser applicant = new AuthenticatedUser(UUID.randomUUID(), "a@example.com", "A", Set.of(Roles.USER));
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", "Admin", Set.of(Roles.USER, Roles.ADMIN));

    @BeforeEach
    void setUp() {
        service = new VerificationService(requests, audits, clubService, userClient, notifications,
                Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));
        when(requests.save(any(VerificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private VerificationRequest stored(VerificationStatus status) {
        VerificationRequest request = new VerificationRequest(applicant.id(), "Chennai Runners");
        request.setCity("Chennai");
        request.setClubType(ClubType.RUNNING);
        request.setStatus(status);
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        return request;
    }

    private static ApplyVerificationRequest application() {
        return new ApplyVerificationRequest("Chennai Runners", ClubType.RUNNING, "Chennai",
                Map.of("instagram", "https://instagram.com/cr"), List.of("https://files/proof.pdf"), "We run", "+91 98765 43210");
    }

    @Test
    void applyCreatesPendingRequest() {
        VerificationRequest request = service.apply(applicant, application());

        assertThat(request.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(request.getProofFiles()).containsExactly("https://files/proof.pdf");
        assertThat(request.getSocialLinks()).containsEntry("instagram", "https://instagram.com/cr");
        verify(audits).save(any());
    }

    @Test
    void cannotApplyTwiceWhileOpen() {
        when(requests.existsByUserIdAndStatusIn(applicant.id(), VerificationStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.apply(applicant, application()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void existingOwnersCannotApply() {
        AuthenticatedUser owner = new AuthenticatedUser(UUID.randomUUID(), "o@x.com", "O", Set.of(Roles.USER, Roles.CLUB_OWNER));

        assertThatThrownBy(() -> service.apply(owner, application()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void approveGrantsRoleCreatesClubAndNotifies() {
        VerificationRequest request = stored(VerificationStatus.PENDING);
        Club club = new Club("Chennai Runners", null, "Chennai", ClubType.RUNNING, null, applicant.id());
        when(clubService.createFor(eq(applicant.id()), any())).thenReturn(club);

        VerificationResponse response = service.approve(admin, request.getId(), new ApproveVerificationRequest(true, null));

        assertThat(response.status()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(response.createdClubId()).isEqualTo(club.getId());
        assertThat(request.getReviewedBy()).isEqualTo(admin.id());
        ArgumentCaptor<UserClient.RoleRequest> role = ArgumentCaptor.forClass(UserClient.RoleRequest.class);
        verify(userClient).addRole(eq(applicant.id()), role.capture());
        assertThat(role.getValue().role()).isEqualTo("CLUB_OWNER");
        ArgumentCaptor<CreateClubRequest> clubRequest = ArgumentCaptor.forClass(CreateClubRequest.class);
        verify(clubService).createFor(eq(applicant.id()), clubRequest.capture());
        assertThat(clubRequest.getValue().name()).isEqualTo("Chennai Runners");
        assertThat(clubRequest.getValue().city()).isEqualTo("Chennai");
        verify(notifications).notifyAfterCommit(eq(applicant.id()), eq("OWNER_VERIFICATION"), anyString());
    }

    @Test
    void approveFailsWhenRoleUpdateFails() {
        VerificationRequest request = stored(VerificationStatus.PENDING);
        when(userClient.addRole(any(), any())).thenThrow(new RuntimeException("user-service down"));

        assertThatThrownBy(() -> service.approve(admin, request.getId(), new ApproveVerificationRequest(true, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(clubService, never()).createFor(any(), any());
        verify(notifications, never()).notifyAfterCommit(any(UUID.class), anyString(), anyString());
    }

    @Test
    void approveChecksClubNameBeforeGrantingRole() {
        VerificationRequest request = stored(VerificationStatus.PENDING);
        when(clubService.nameTaken("Chennai Runners")).thenReturn(true);

        assertThatThrownBy(() -> service.approve(admin, request.getId(), new ApproveVerificationRequest(true, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userClient, never()).addRole(any(), any());
    }

    @Test
    void cannotApproveClosedRequest() {
        VerificationRequest request = stored(VerificationStatus.REJECTED);

        assertThatThrownBy(() -> service.approve(admin, request.getId(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cannotApproveWhileWaitingForInfo() {
        VerificationRequest request = stored(VerificationStatus.NEEDS_INFO);

        assertThatThrownBy(() -> service.approve(admin, request.getId(), null)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectStoresReason() {
        VerificationRequest request = stored(VerificationStatus.PENDING);

        service.reject(admin, request.getId(), "Could not verify the club");

        assertThat(request.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(request.getAdminNotes()).isEqualTo("Could not verify the club");
    }

    @Test
    void needsInfoThenUploadMovesToUnderReview() {
        VerificationRequest request = stored(VerificationStatus.PENDING);

        service.requestInfo(admin, request.getId(), "Please add your Strava club link");
        assertThat(request.getStatus()).isEqualTo(VerificationStatus.NEEDS_INFO);

        service.uploadProof(applicant, request.getId(), new UploadProofRequest(List.of("https://files/strava.png"), null));
        assertThat(request.getStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
        assertThat(request.getProofFiles()).contains("https://files/strava.png");
    }

    @Test
    void onlyApplicantCanUploadProof() {
        VerificationRequest request = stored(VerificationStatus.PENDING);

        assertThatThrownBy(() -> service.uploadProof(admin, request.getId(), new UploadProofRequest(List.of("https://f/x"), null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void othersCannotViewRequest() {
        VerificationRequest request = stored(VerificationStatus.PENDING);
        AuthenticatedUser stranger = new AuthenticatedUser(UUID.randomUUID(), "s@x.com", "S", Set.of(Roles.USER));

        assertThatThrownBy(() -> service.getVisibleTo(stranger, request.getId())).isInstanceOf(ApiException.class);
        assertThat(service.getVisibleTo(admin, request.getId())).isSameAs(request);
    }
}

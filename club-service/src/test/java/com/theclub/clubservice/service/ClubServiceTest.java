package com.theclub.clubservice.service;

import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.CreateClubRequest;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubMembership;
import com.theclub.clubservice.model.ClubType;
import com.theclub.clubservice.model.MembershipRole;
import com.theclub.clubservice.repository.ClubMembershipRepository;
import com.theclub.clubservice.repository.ClubRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock
    ClubRepository clubs;
    @Mock
    ClubMembershipRepository memberships;
    @Mock
    UserClient userClient;
    @InjectMocks
    ClubService clubService;

    private static AuthenticatedUser user(String... roles) {
        return new AuthenticatedUser(UUID.randomUUID(), "u@example.com", "U", Set.of(roles));
    }

    private static CreateClubRequest request(String name) {
        return new CreateClubRequest(name, "Weekend running club", "Chennai", ClubType.RUNNING, null);
    }

    @Test
    void plainUsersCannotCreateClubs() {
        assertThatThrownBy(() -> clubService.create(user(Roles.USER), request("Chennai Runners")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo("Only verified club owners can create clubs");
                });
        verify(clubs, never()).save(any());
    }

    @Test
    void ownerCreatesClubAndBecomesOwnerMember() {
        AuthenticatedUser owner = user(Roles.USER, Roles.CLUB_OWNER);
        when(clubs.existsByNameIgnoreCase("Chennai Runners")).thenReturn(false);
        when(clubs.save(any(Club.class))).thenAnswer(inv -> inv.getArgument(0));

        Club club = clubService.create(owner, request("  Chennai Runners "));

        assertThat(club.getOwnerId()).isEqualTo(owner.id());
        assertThat(club.getName()).isEqualTo("Chennai Runners");
        ArgumentCaptor<ClubMembership> membership = ArgumentCaptor.forClass(ClubMembership.class);
        verify(memberships).save(membership.capture());
        assertThat(membership.getValue().getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(membership.getValue().getUserId()).isEqualTo(owner.id());
    }

    @Test
    void adminsMayCreateClubs() {
        when(clubs.existsByNameIgnoreCase("Admins")).thenReturn(false);
        when(clubs.save(any(Club.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(clubService.create(user(Roles.USER, Roles.ADMIN), request("Admins"))).isNotNull();
    }

    @Test
    void duplicateNameIsConflict() {
        when(clubs.existsByNameIgnoreCase("Chennai Runners")).thenReturn(true);

        assertThatThrownBy(() -> clubService.create(user(Roles.CLUB_OWNER), request("Chennai Runners")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void joiningTwiceReturnsExistingMembership() {
        Club club = new Club("C", null, null, null, null, UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        ClubMembership existing = new ClubMembership(club.getId(), userId, MembershipRole.MEMBER);
        when(clubs.findById(club.getId())).thenReturn(Optional.of(club));
        when(memberships.findByClubIdAndUserId(club.getId(), userId)).thenReturn(Optional.of(existing));

        ClubService.JoinResult result = clubService.join(userId, club.getId());

        assertThat(result.created()).isFalse();
        assertThat(result.membership()).isSameAs(existing);
        verify(memberships, never()).save(any());
    }

    @Test
    void firstJoinCreatesMemberMembership() {
        Club club = new Club("C", null, null, null, null, UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        when(clubs.findById(club.getId())).thenReturn(Optional.of(club));
        when(memberships.findByClubIdAndUserId(club.getId(), userId)).thenReturn(Optional.empty());
        when(memberships.save(any(ClubMembership.class))).thenAnswer(inv -> inv.getArgument(0));

        ClubService.JoinResult result = clubService.join(userId, club.getId());

        assertThat(result.created()).isTrue();
        assertThat(result.membership().getRole()).isEqualTo(MembershipRole.MEMBER);
    }

    @Test
    void ownerCannotLeave() {
        UUID ownerId = UUID.randomUUID();
        Club club = new Club("C", null, null, null, null, ownerId);
        when(clubs.findById(club.getId())).thenReturn(Optional.of(club));
        when(memberships.findByClubIdAndUserId(club.getId(), ownerId))
                .thenReturn(Optional.of(new ClubMembership(club.getId(), ownerId, MembershipRole.OWNER)));

        assertThatThrownBy(() -> clubService.leave(ownerId, club.getId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(memberships, never()).delete(any());
    }

    @Test
    void namesFallBackToEmptyWhenUserServiceFails() {
        when(userClient.summaries(any())).thenThrow(new RuntimeException("down"));

        assertThat(clubService.namesOf(List.of(UUID.randomUUID()))).isEmpty();
    }
}

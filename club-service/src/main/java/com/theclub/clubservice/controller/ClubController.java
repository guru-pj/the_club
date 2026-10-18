package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.ClubResponse;
import com.theclub.clubservice.dto.CreateClubRequest;
import com.theclub.clubservice.dto.MembershipResponse;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.UpdateClubRequest;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.service.ClubService;
import com.theclub.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClubResponse create(@Valid @RequestBody CreateClubRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        Club club = clubService.create(caller, request);
        return clubService.describe(club, caller.id());
    }

    @GetMapping
    public PageResponse<ClubResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String city,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @AuthenticationPrincipal AuthenticatedUser caller) {
        return clubService.search(q, city, page, size, caller.id());
    }

    @GetMapping("/me")
    public List<ClubResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller) {
        return clubService.joinedBy(caller.id(), caller.id());
    }

    @GetMapping("/owner/{ownerId}")
    public List<ClubResponse> ownedBy(@PathVariable UUID ownerId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return clubService.ownedBy(ownerId, caller.id());
    }

    @GetMapping("/user/{userId}")
    public List<ClubResponse> joinedBy(@PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return clubService.joinedBy(userId, caller.id());
    }

    @GetMapping("/{id}")
    public ClubResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return clubService.describe(id, caller.id());
    }

    @PutMapping("/{id}")
    public ClubResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateClubRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return clubService.describe(clubService.update(caller, id, request), caller.id());
    }

    @PostMapping("/{clubId}/join")
    public ResponseEntity<MembershipResponse> join(@PathVariable UUID clubId,
                                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        ClubService.JoinResult result = clubService.join(caller.id(), clubId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(MembershipResponse.of(result.membership(), caller.name()));
    }

    @DeleteMapping("/{clubId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID clubId, @AuthenticationPrincipal AuthenticatedUser caller) {
        clubService.leave(caller.id(), clubId);
    }

    @GetMapping("/{clubId}/members")
    public PageResponse<MembershipResponse> members(@PathVariable UUID clubId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return clubService.members(clubId, page, size);
    }

    @DeleteMapping("/{clubId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID clubId, @PathVariable UUID userId,
                             @AuthenticationPrincipal AuthenticatedUser caller) {
        clubService.removeMember(caller, clubId, userId);
    }
}

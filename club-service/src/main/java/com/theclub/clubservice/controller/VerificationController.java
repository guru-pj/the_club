package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.ApplyVerificationRequest;
import com.theclub.clubservice.dto.ApplyVerificationResponse;
import com.theclub.clubservice.dto.ApproveVerificationRequest;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.RejectVerificationRequest;
import com.theclub.clubservice.dto.RequestInfoRequest;
import com.theclub.clubservice.dto.UploadProofRequest;
import com.theclub.clubservice.dto.VerificationResponse;
import com.theclub.clubservice.model.VerificationAudit;
import com.theclub.clubservice.model.VerificationRequest;
import com.theclub.clubservice.model.VerificationStatus;
import com.theclub.clubservice.service.VerificationService;
import com.theclub.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Club owner verification (see verification_flow.txt / api_endpoints.md).
 */
@RestController
@RequestMapping("/api/v1/owner-verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplyVerificationResponse apply(@Valid @RequestBody ApplyVerificationRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser caller) {
        VerificationRequest saved = verificationService.apply(caller, request);
        return new ApplyVerificationResponse(saved.getId(), saved.getStatus());
    }

    @GetMapping("/requests/me")
    public List<VerificationResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller) {
        return verificationService.mine(caller.id()).stream().map(VerificationResponse::of).toList();
    }

    @GetMapping("/requests/{id}")
    public VerificationResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return VerificationResponse.of(verificationService.getVisibleTo(caller, id));
    }

    @GetMapping("/requests/{id}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<VerificationAudit> history(@PathVariable UUID id) {
        return verificationService.history(id);
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<VerificationResponse> list(@RequestParam(required = false) VerificationStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return verificationService.list(status, page, size);
    }

    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResponse approve(@PathVariable UUID id,
                                        @Valid @RequestBody(required = false) ApproveVerificationRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser caller) {
        return verificationService.approve(caller, id, request);
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectVerificationRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return VerificationResponse.of(verificationService.reject(caller, id, request.reason()));
    }

    @PostMapping("/requests/{id}/request-info")
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResponse requestInfo(@PathVariable UUID id, @Valid @RequestBody RequestInfoRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser caller) {
        return VerificationResponse.of(verificationService.requestInfo(caller, id, request.message()));
    }

    @PostMapping("/requests/{id}/upload-proof")
    public VerificationResponse uploadProof(@PathVariable UUID id, @Valid @RequestBody UploadProofRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser caller) {
        return VerificationResponse.of(verificationService.uploadProof(caller, id, request));
    }
}

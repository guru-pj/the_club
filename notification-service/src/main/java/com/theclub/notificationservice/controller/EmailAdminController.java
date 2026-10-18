package com.theclub.notificationservice.controller;

import com.theclub.notificationservice.dto.EmailOutboxResponse;
import com.theclub.notificationservice.dto.PageResponse;
import com.theclub.notificationservice.email.EmailOutboxService;
import com.theclub.notificationservice.model.EmailStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operators' view of email delivery: find failed emails and send them again.
 */
@RestController
@RequestMapping("/api/v1/notifications/admin/email-outbox")
@PreAuthorize("hasRole('ADMIN')")
public class EmailAdminController {

    private final EmailOutboxService outbox;

    public EmailAdminController(EmailOutboxService outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    public PageResponse<EmailOutboxResponse> list(@RequestParam(required = false) EmailStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(outbox.list(status, page, size), EmailOutboxResponse::from);
    }

    @PostMapping("/{id}/retry")
    public EmailOutboxResponse retry(@PathVariable UUID id) {
        return EmailOutboxResponse.from(outbox.retry(id));
    }
}

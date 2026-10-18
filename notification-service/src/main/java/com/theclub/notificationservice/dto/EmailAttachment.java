package com.theclub.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param inline    when true the attachment is embedded and referenced from the HTML body as {@code cid:<contentId>}.
 */
public record EmailAttachment(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 100) String contentType,
        @NotBlank String base64Content,
        boolean inline,
        @Size(max = 100) String contentId) {
}

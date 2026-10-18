package com.theclub.notificationservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.theclub.common.web.ApiException;
import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.email.BounceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * ZeptoMail bounce webhook (public). ZeptoMail can't sign webhook calls, so the URL carries a shared secret:
 * {@code /api/v1/notifications/webhooks/zeptomail?token=<ZEPTOMAIL_WEBHOOK_TOKEN>} (or an X-Webhook-Token header).
 */
@RestController
@RequestMapping("/api/v1/notifications/webhooks")
public class EmailWebhookController {

    private final BounceService bounces;
    private final MailProperties properties;

    public EmailWebhookController(BounceService bounces, MailProperties properties) {
        this.bounces = bounces;
        this.properties = properties;
    }

    @PostMapping("/zeptomail")
    public Map<String, Object> zeptomail(@RequestParam(name = "token", required = false) String queryToken,
                                         @RequestHeader(name = "X-Webhook-Token", required = false) String headerToken,
                                         @RequestBody JsonNode payload) {
        String expected = properties.webhook().token();
        if (expected.isBlank()) {
            throw ApiException.notFound("Webhook not configured");
        }
        String presented = headerToken != null ? headerToken : queryToken;
        if (presented == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.forbidden("Invalid webhook token");
        }
        return Map.of("suppressed", bounces.handle(payload).size());
    }
}

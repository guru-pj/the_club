package com.theclub.notificationservice.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailAttachment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends through the Zoho ZeptoMail transactional API (POST /v1.1/email).
 * Authentication is the Mail Agent's Send Mail token: {@code Authorization: Zoho-enczapikey <token>}.
 */
public class ZeptoMailEmailProvider implements EmailProvider {

    private final RestClient client;
    private final MailProperties.ZeptoMail settings;
    private final ObjectMapper json;

    public ZeptoMailEmailProvider(RestClient client, MailProperties.ZeptoMail settings, ObjectMapper json) {
        if (settings.token().isBlank()) {
            throw new IllegalStateException("MAIL_PROVIDER=zeptomail needs a ZeptoMail Send Mail token: "
                    + "put it in secrets/zeptomail-token (ZEPTOMAIL_TOKEN). See docs/EMAIL_SETUP.md");
        }
        this.client = client;
        this.settings = settings;
        this.json = json;
    }

    @Override
    public String name() {
        return MailProperties.ZEPTOMAIL;
    }

    @Override
    public String send(OutgoingEmail email) {
        Map<String, Object> body = requestBody(email);
        try {
            return client.post()
                    .uri(settings.apiUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Zoho-enczapikey " + stripPrefix(settings.token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        String raw = readBody(response.getBody());
                        if (status >= 200 && status < 300) {
                            return text(raw, "request_id");
                        }
                        boolean retryable = status == 429 || status >= 500;
                        throw new EmailDeliveryException("ZeptoMail rejected the email (HTTP " + status + "): "
                                + describeError(raw), retryable);
                    });
        } catch (ResourceAccessException e) {
            throw new EmailDeliveryException("ZeptoMail unreachable: " + e.getMessage(), true, e);
        }
    }

    Map<String, Object> requestBody(OutgoingEmail email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", address(email.fromAddress(), email.fromName()));
        body.put("to", List.of(Map.of("email_address", Map.of("address", email.to()))));
        if (email.replyTo() != null && !email.replyTo().isBlank()) {
            body.put("reply_to", List.of(Map.of("address", email.replyTo())));
        }
        body.put("subject", email.subject());
        body.put("htmlbody", email.htmlBody());
        body.put("textbody", email.textBody());
        List<Map<String, Object>> attachments = new ArrayList<>();
        List<Map<String, Object>> inlineImages = new ArrayList<>();
        for (EmailAttachment a : email.attachments()) {
            if (a.inline()) {
                inlineImages.add(Map.of("cid", a.contentId() != null ? a.contentId() : a.filename(),
                        "content", a.base64Content(), "mime_type", a.contentType()));
            } else {
                attachments.add(Map.of("content", a.base64Content(), "mime_type", a.contentType(), "name", a.filename()));
            }
        }
        if (!attachments.isEmpty()) {
            body.put("attachments", attachments);
        }
        if (!inlineImages.isEmpty()) {
            body.put("inline_images", inlineImages);
        }
        if (email.reference() != null) {
            body.put("client_reference", email.reference());
        }
        body.put("track_clicks", false);
        body.put("track_opens", false);
        return body;
    }

    private static Map<String, Object> address(String address, String name) {
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("address", address);
        if (name != null && !name.isBlank()) {
            from.put("name", name);
        }
        return from;
    }

    /** Accept tokens pasted with or without the "Zoho-enczapikey " prefix. */
    private static String stripPrefix(String token) {
        String prefix = "Zoho-enczapikey ";
        return token.regionMatches(true, 0, prefix, 0, prefix.length()) ? token.substring(prefix.length()).trim() : token;
    }

    private static String readBody(java.io.InputStream in) {
        try {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String text(String raw, String field) {
        try {
            JsonNode node = json.readTree(raw).get(field);
            return node == null || node.isNull() ? null : node.asText();
        } catch (IOException e) {
            return null;
        }
    }

    /** Error code and message from the response, e.g. "TM_3501 Invalid API Token found" (never the token). */
    private String describeError(String raw) {
        try {
            JsonNode error = json.readTree(raw).path("error");
            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            JsonNode details = error.path("details");
            if (details.isArray() && !details.isEmpty()) {
                JsonNode first = details.get(0);
                message = message + " (" + first.path("code").asText("") + ": " + first.path("message").asText("")
                        + (first.hasNonNull("target") ? " [" + first.get("target").asText() + "]" : "") + ")";
            }
            String described = (code + " " + message).trim();
            return described.isEmpty() ? "no error details" : described;
        } catch (IOException e) {
            return "unparseable error body";
        }
    }
}

package com.theclub.notificationservice.email;

import com.theclub.common.web.ApiException;
import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailRequest;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns an {@link EmailRequest} into the exact message that will be delivered: validates the recipient,
 * renders the template, guards the subject against header injection and applies the staging redirect.
 */
@Component
public class EmailComposer {

    public record Composed(String recipient, String subject, String template, String html, String text) {
    }

    static final int MAX_SUBJECT = 500;
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n\\u0085\\u2028\\u2029]+");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern BLOCK_ENDS = Pattern.compile("(?i)</(p|div|h[1-6]|li|tr)>|<br\\s*/?>");

    private final EmailRenderer renderer;
    private final MailProperties properties;

    public EmailComposer(EmailRenderer renderer, MailProperties properties) {
        this.renderer = renderer;
        this.properties = properties;
    }

    public Composed compose(EmailRequest request) {
        String recipient = validAddress(request.to());
        String subject;
        String html;
        String text;
        String template = null;
        if (request.isTemplated()) {
            EmailTemplate t = EmailTemplate.byId(request.template())
                    .orElseThrow(() -> ApiException.badRequest("Unknown email template '" + request.template() + "'"));
            List<String> missing = t.required().stream()
                    .filter(name -> request.variables().get(name) == null || request.variables().get(name).toString().isBlank())
                    .toList();
            if (!missing.isEmpty()) {
                throw ApiException.badRequest("Email template '" + t.id() + "' is missing variables " + missing);
            }
            EmailRenderer.Rendered rendered = renderer.render(t, request.variables());
            subject = rendered.subject();
            html = rendered.html();
            text = rendered.text();
            template = t.id();
        } else {
            subject = request.subject();
            html = request.body();
            text = htmlToText(request.body());
        }
        subject = safeSubject(subject);
        if (properties.redirecting()) {
            subject = safeSubject("[to " + recipient + "] " + subject);
            recipient = validAddress(properties.redirectAllTo());
        }
        return new Composed(recipient, subject, template, html, text);
    }

    /** Removes line breaks (the classic header-injection vector) and caps the length. */
    static String safeSubject(String subject) {
        String clean = LINE_BREAKS.matcher(subject == null ? "" : subject).replaceAll(" ").strip();
        return clean.length() <= MAX_SUBJECT ? clean : clean.substring(0, MAX_SUBJECT);
    }

    static String validAddress(String address) {
        String trimmed = address == null ? "" : address.strip();
        if (trimmed.isEmpty() || LINE_BREAKS.matcher(trimmed).find()) {
            throw ApiException.badRequest("Invalid email address");
        }
        try {
            InternetAddress parsed = new InternetAddress(trimmed, true);
            parsed.validate();
            return parsed.getAddress();
        } catch (AddressException e) {
            throw ApiException.badRequest("Invalid email address");
        }
    }

    /** A readable plain-text alternative for legacy raw-HTML emails. */
    static String htmlToText(String html) {
        String withBreaks = BLOCK_ENDS.matcher(html).replaceAll("\n");
        String text = HtmlUtils.htmlUnescape(TAGS.matcher(withBreaks).replaceAll(""));
        return text.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("").replaceAll("\n{3,}", "\n\n").strip() + "\n";
    }
}

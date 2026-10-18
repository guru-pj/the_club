package com.theclub.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Email delivery settings ({@code theclub.mail.*}).
 *
 * @param provider      {@code zeptomail}, {@code smtp} or {@code log}. When blank the legacy {@code enabled} flag decides
 *                      ({@code true} → smtp, {@code false} → log).
 * @param enabled       legacy switch kept for existing deployments (MAIL_ENABLED).
 * @param from          sender, e.g. "The Club &lt;no-reply@theclub.example&gt;". Must be on a domain verified with the provider.
 * @param replyTo       optional reply-to address.
 * @param redirectAllTo staging safety net: when set, every email goes to this address instead of the real recipient.
 */
@ConfigurationProperties(prefix = "theclub.mail")
public record MailProperties(String provider, boolean enabled, String from, String replyTo, String redirectAllTo,
                             ZeptoMail zeptomail, Dispatcher dispatcher, Webhook webhook) {

    public static final String ZEPTOMAIL = "zeptomail";
    public static final String SMTP = "smtp";
    public static final String LOG = "log";

    public MailProperties {
        if (from == null || from.isBlank()) {
            from = "The Club <no-reply@theclub.local>";
        }
        zeptomail = zeptomail == null ? new ZeptoMail(null, null, null, null) : zeptomail;
        dispatcher = dispatcher == null ? new Dispatcher(true, null, 0, 0, null) : dispatcher;
        webhook = webhook == null ? new Webhook(null) : webhook;
    }

    /** The provider actually in use, after applying the legacy {@code enabled} fallback. */
    public String resolvedProvider() {
        if (provider != null && !provider.isBlank()) {
            return provider.trim().toLowerCase();
        }
        return enabled ? SMTP : LOG;
    }

    public boolean redirecting() {
        return redirectAllTo != null && !redirectAllTo.isBlank();
    }

    /**
     * @param apiUrl regional endpoint, e.g. https://api.zeptomail.in/v1.1/email (India) or https://api.zeptomail.com/v1.1/email.
     * @param token  the Mail Agent's "Send Mail" token (without the "Zoho-enczapikey " prefix).
     */
    public record ZeptoMail(String apiUrl, String token, Duration connectTimeout, Duration readTimeout) {

        public ZeptoMail {
            apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.zeptomail.in/v1.1/email" : apiUrl.trim();
            token = token == null ? "" : token.trim();
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        }
    }

    /**
     * @param enabled     turn the background sender off (tests, or a replica that should only accept requests).
     * @param interval    pause between dispatch runs.
     * @param batchSize   emails claimed per run.
     * @param maxAttempts delivery attempts before an email is marked FAILED.
     * @param stuckAfter  a claimed email still SENDING after this long is put back in the queue.
     */
    public record Dispatcher(boolean enabled, Duration interval, int batchSize, int maxAttempts, Duration stuckAfter) {

        public Dispatcher {
            interval = interval == null ? Duration.ofSeconds(5) : interval;
            batchSize = batchSize <= 0 ? 20 : batchSize;
            maxAttempts = maxAttempts <= 0 ? 8 : maxAttempts;
            stuckAfter = stuckAfter == null ? Duration.ofMinutes(10) : stuckAfter;
        }
    }

    /**
     * @param token shared secret the ZeptoMail bounce webhook must present; blank disables the webhook.
     */
    public record Webhook(String token) {

        public Webhook {
            token = token == null ? "" : token.trim();
        }
    }
}

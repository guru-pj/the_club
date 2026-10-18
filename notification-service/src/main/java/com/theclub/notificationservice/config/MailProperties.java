package com.theclub.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled when false emails are only logged (no SMTP server needed).
 * @param from    sender address, e.g. "The Club &lt;no-reply@theclub.local&gt;".
 */
@ConfigurationProperties(prefix = "theclub.mail")
public record MailProperties(boolean enabled, String from) {

    public MailProperties {
        if (from == null || from.isBlank()) {
            from = "The Club <no-reply@theclub.local>";
        }
    }
}

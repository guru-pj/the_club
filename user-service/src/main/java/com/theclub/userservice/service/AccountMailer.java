package com.theclub.userservice.service;

import com.theclub.userservice.client.AuthClient;
import com.theclub.userservice.client.NotificationClient;
import com.theclub.userservice.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Account emails and session revocation. Both run after the surrounding transaction commits and are
 * best effort: failures are logged (without the token) and never undo the account change.
 */
@Component
public class AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(AccountMailer.class);

    private final NotificationClient notifications;
    private final AuthClient auth;
    private final String baseUrl;

    public AccountMailer(NotificationClient notifications, AuthClient auth,
                         @Value("${theclub.app.base-url:http://localhost:3000}") String baseUrl) {
        this.notifications = notifications;
        this.auth = auth;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void sendVerification(User user, String token) {
        String link = link("/verify-email", token);
        send(user, "EMAIL_VERIFICATION", "Please verify your email address",
                "Verify your email for The Club",
                paragraph("Hi " + user.getName() + ",")
                        + paragraph("Welcome to The Club! Please confirm your email address to finish setting up your account.")
                        + button(link, "Verify email address")
                        + paragraph("This link expires in 48 hours. If you didn't create an account, you can ignore this email."));
    }

    public void sendPasswordReset(User user, String token) {
        String link = link("/reset-password", token);
        send(user, "PASSWORD_RESET", "A password reset was requested for your account",
                "Reset your The Club password",
                paragraph("Hi " + user.getName() + ",")
                        + paragraph("We received a request to reset your password. Choose a new one using the link below.")
                        + button(link, "Reset password")
                        + paragraph("This link expires in 30 minutes and can be used once. If you didn't ask for this, "
                        + "you can ignore this email — your password stays the same."));
    }

    public void sendPasswordChanged(User user) {
        send(user, "PASSWORD_CHANGED", "Your password was changed",
                "Your The Club password was changed",
                paragraph("Hi " + user.getName() + ",")
                        + paragraph("Your password was just changed and you were signed out on all devices.")
                        + paragraph("If this wasn't you, reset your password immediately and contact the club admins."));
    }

    public void revokeSessions(UUID userId) {
        afterCommit(() -> {
            try {
                auth.revokeSessions(userId);
            } catch (RuntimeException e) {
                log.error("Could not revoke sessions of user {} after a password change: {}", userId, e.getMessage());
            }
        });
    }

    private void send(User user, String type, String message, String subject, String htmlBody) {
        var notification = new NotificationClient.Notification(user.getId(), type, message,
                new NotificationClient.Email(user.getEmail(), subject, htmlBody));
        afterCommit(() -> {
            try {
                notifications.send(notification);
            } catch (RuntimeException e) {
                log.warn("Could not send {} email to user {}: {}", type, user.getId(), e.getMessage());
            }
        });
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(baseUrl + path).queryParam("token", token).build().toUriString();
    }

    private static String paragraph(String text) {
        return "<p>" + HtmlUtils.htmlEscape(text) + "</p>";
    }

    private static String button(String href, String label) {
        String url = HtmlUtils.htmlEscape(href);
        return "<p><a href=\"" + url + "\" style=\"display:inline-block;padding:10px 18px;background:#0f766e;"
                + "color:#ffffff;border-radius:8px;text-decoration:none;font-weight:600\">" + HtmlUtils.htmlEscape(label)
                + "</a></p><p style=\"color:#5f6b65;font-size:13px\">Or paste this link into your browser: " + url + "</p>";
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

package com.theclub.eventservice.service;

import com.theclub.eventservice.client.NotificationClient;
import com.theclub.eventservice.client.NotificationClient.EmailAttachment;
import com.theclub.eventservice.client.NotificationClient.EmailMessage;
import com.theclub.eventservice.client.NotificationClient.NotificationRequest;
import com.theclub.eventservice.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sends registration confirmations (in-app + email with the QR ticket) and cancellation notices.
 * Delivery runs after the transaction commits and is best effort: failures are logged, never propagated.
 */
@Component
public class ConfirmationNotifier {

    public static final String TYPE_REGISTRATION_CONFIRMED = "EVENT_REGISTRATION_CONFIRMED";
    public static final String TYPE_EVENT_CANCELLED = "EVENT_CANCELLED";
    static final String QR_CONTENT_ID = "ticket-qr";

    private static final Logger log = LoggerFactory.getLogger(ConfirmationNotifier.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy 'at' hh:mm a", Locale.ENGLISH);

    private final NotificationClient notificationClient;
    private final QrCodeService qrCodeService;

    public ConfirmationNotifier(NotificationClient notificationClient, QrCodeService qrCodeService) {
        this.notificationClient = notificationClient;
        this.qrCodeService = qrCodeService;
    }

    public void registrationConfirmed(UUID userId, String email, Event event, String ticketCode) {
        String title = event.getTitle();
        LocalDateTime startTime = event.getStartTime();
        String location = event.getLocation();
        afterCommit(() -> {
            String message = "You're registered for " + title + " on " + DATE_FORMAT.format(startTime);
            EmailMessage emailMessage = null;
            if (email != null && !email.isBlank()) {
                String png = Base64.getEncoder().encodeToString(qrCodeService.ticketPng(ticketCode));
                emailMessage = new EmailMessage(email, "Your ticket: " + title,
                        ticketEmailHtml(title, startTime, location, ticketCode),
                        List.of(new EmailAttachment("ticket.png", "image/png", png, true, QR_CONTENT_ID)));
            }
            notificationClient.create(new NotificationRequest(userId, TYPE_REGISTRATION_CONFIRMED, message, emailMessage));
        });
    }

    public void eventCancelled(Event event, Collection<UUID> userIds) {
        String message = event.getTitle() + " on " + DATE_FORMAT.format(event.getStartTime()) + " has been cancelled";
        List<UUID> recipients = List.copyOf(userIds);
        afterCommit(() -> recipients.forEach(userId -> {
            try {
                notificationClient.create(new NotificationRequest(userId, TYPE_EVENT_CANCELLED, message, null));
            } catch (RuntimeException e) {
                log.warn("Could not notify user {} about cancellation: {}", userId, e.getMessage());
            }
        }));
    }

    static String ticketEmailHtml(String title, LocalDateTime startTime, String location, String ticketCode) {
        String where = location == null ? "" : "<p><strong>Where:</strong> " + HtmlUtils.htmlEscape(location) + "</p>";
        return """
                <h2>You're in! 🎉</h2>
                <p>Your registration for <strong>%s</strong> is confirmed.</p>
                <p><strong>When:</strong> %s</p>
                %s
                <p>Show this QR code to the club owner at check-in:</p>
                <p><img src="cid:%s" alt="Ticket QR code" width="240" height="240"/></p>
                <p>Ticket code: <code>%s</code></p>
                """.formatted(HtmlUtils.htmlEscape(title), DATE_FORMAT.format(startTime), where, QR_CONTENT_ID,
                ticketCode);
    }

    private static void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Notification delivery failed: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}

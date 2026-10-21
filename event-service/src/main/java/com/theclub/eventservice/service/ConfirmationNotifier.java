package com.theclub.eventservice.service;

import com.theclub.eventservice.client.ClubClient;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sends registration confirmations (in-app + email with the QR ticket) and cancellation notices.
 * Delivery runs after the transaction commits and is best effort: failures are logged, never propagated.
 */
@Component
public class ConfirmationNotifier {

    public static final String TYPE_REGISTRATION_CONFIRMED = "EVENT_REGISTRATION_CONFIRMED";
    public static final String TYPE_EVENT_CANCELLED = "EVENT_CANCELLED";
    public static final String TYPE_PAYMENT_REFUNDED = "PAYMENT_REFUNDED";
    public static final String TYPE_EVENT_ANNOUNCED = "EVENT_ANNOUNCED";
    static final String QR_CONTENT_ID = "ticket-qr";

    private static final Logger log = LoggerFactory.getLogger(ConfirmationNotifier.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy 'at' hh:mm a", Locale.ENGLISH);

    private final NotificationClient notificationClient;
    private final QrCodeService qrCodeService;
    private final MemberEmails memberEmails;
    private final ClubClient clubClient;

    public ConfirmationNotifier(NotificationClient notificationClient, QrCodeService qrCodeService,
                                MemberEmails memberEmails, ClubClient clubClient) {
        this.notificationClient = notificationClient;
        this.qrCodeService = qrCodeService;
        this.memberEmails = memberEmails;
        this.clubClient = clubClient;
    }

    /**
     * In-app announcement of a new event to the club's subscribers and followers (not the author); best effort,
     * after commit, so a slow or failing club-service never blocks creating the event.
     */
    public void eventAnnounced(Event event, String clubName, UUID authorId) {
        String message = clubName + ": " + event.getTitle() + " on " + DATE_FORMAT.format(event.getStartTime());
        UUID clubId = event.getClubId();
        afterCommit(() -> clubClient.audience(clubId).stream()
                .filter(userId -> !userId.equals(authorId))
                .forEach(userId -> {
                    try {
                        notificationClient.create(new NotificationRequest(userId, TYPE_EVENT_ANNOUNCED, message, null));
                    } catch (RuntimeException e) {
                        log.warn("Could not announce event {} to user {}: {}", event.getId(), userId, e.getMessage());
                    }
                }));
    }

    public void registrationConfirmed(UUID userId, String email, Event event, String ticketCode) {
        registrationConfirmedLazily(userId, () -> email, event, ticketCode);
    }

    /**
     * @param email resolved after commit, so a remote lookup never runs inside the database transaction.
     */
    public void registrationConfirmedLazily(UUID userId, Supplier<String> email, Event event, String ticketCode) {
        String title = event.getTitle();
        LocalDateTime startTime = event.getStartTime();
        String location = event.getLocation();
        afterCommit(() -> {
            String message = "You're registered for " + title + " on " + DATE_FORMAT.format(startTime);
            EmailMessage emailMessage = null;
            String to = email.get();
            if (to != null && !to.isBlank()) {
                String png = Base64.getEncoder().encodeToString(qrCodeService.ticketPng(ticketCode));
                emailMessage = new EmailMessage(to, "registration-confirmed",
                        ticketEmailVariables(title, startTime, location, ticketCode),
                        List.of(new EmailAttachment("ticket.png", "image/png", png, true, QR_CONTENT_ID)));
            }
            notificationClient.create(new NotificationRequest(userId, TYPE_REGISTRATION_CONFIRMED, message, emailMessage));
        });
    }

    /**
     * In-app notice plus an email to every affected member (emails looked up after commit; best effort).
     */
    public void eventCancelled(Event event, Collection<UUID> userIds) {
        String title = event.getTitle();
        String when = DATE_FORMAT.format(event.getStartTime());
        String message = title + " on " + when + " has been cancelled";
        boolean paid = event.getPrice() != null && event.getPrice().signum() > 0;
        List<UUID> recipients = List.copyOf(userIds);
        afterCommit(() -> recipients.forEach(userId -> {
            try {
                EmailMessage emailMessage = null;
                String to = memberEmails.emailOf(userId);
                if (to != null && !to.isBlank()) {
                    Map<String, Object> variables = new HashMap<>(Map.of("eventTitle", title, "when", when));
                    if (paid) {
                        variables.put("refundNote", "If you paid for this event, your payment is refunded automatically "
                                + "to the original payment method.");
                    }
                    emailMessage = new EmailMessage(to, "event-cancelled", variables, null);
                }
                notificationClient.create(new NotificationRequest(userId, TYPE_EVENT_CANCELLED, message, emailMessage));
            } catch (RuntimeException e) {
                log.warn("Could not notify user {} about cancellation: {}", userId, e.getMessage());
            }
        }));
    }

    /**
     * Tells the member a refund was initiated. The email may resolve to null (in-app only).
     */
    public void paymentRefunded(UUID userId, Supplier<String> email, Event event, BigDecimal amount, String currency,
                                String reason) {
        String title = event.getTitle();
        String money = amount.toPlainString() + " " + currency;
        afterCommit(() -> {
            String message = "A refund of " + money + " for " + title + " has been initiated"
                    + (reason == null || reason.isBlank() ? "" : ": " + reason);
            EmailMessage emailMessage = null;
            String to = email.get();
            if (to != null && !to.isBlank()) {
                Map<String, Object> variables = new HashMap<>(Map.of("eventTitle", title, "amount", money));
                if (reason != null && !reason.isBlank()) {
                    variables.put("reason", reason);
                }
                emailMessage = new EmailMessage(to, "payment-refunded", variables, null);
            }
            notificationClient.create(new NotificationRequest(userId, TYPE_PAYMENT_REFUNDED, message, emailMessage));
        });
    }

    static Map<String, Object> ticketEmailVariables(String title, LocalDateTime startTime, String location,
                                                    String ticketCode) {
        Map<String, Object> variables = new HashMap<>(Map.of("eventTitle", title, "when", DATE_FORMAT.format(startTime),
                "ticketCode", ticketCode, "qrCid", QR_CONTENT_ID));
        if (location != null && !location.isBlank()) {
            variables.put("location", location);
        }
        return variables;
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

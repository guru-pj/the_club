package com.theclub.notificationservice.email;

import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.model.EmailOutbox;
import com.theclub.notificationservice.repository.EmailSuppressionRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Delivers queued emails in the background. Safe to run on every replica: claims use SKIP LOCKED.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final EmailOutboxService outbox;
    private final EmailProvider provider;
    private final EmailSuppressionRepository suppressions;
    private final EmailMetrics metrics;
    private final MailProperties properties;
    private final InternetAddress from;

    public EmailDispatcher(EmailOutboxService outbox, EmailProvider provider, EmailSuppressionRepository suppressions,
                           EmailMetrics metrics, MailProperties properties) {
        this.outbox = outbox;
        this.provider = provider;
        this.suppressions = suppressions;
        this.metrics = metrics;
        this.properties = properties;
        try {
            this.from = new InternetAddress(properties.from(), true);
        } catch (AddressException e) {
            throw new IllegalStateException("MAIL_FROM is not a valid address: " + properties.from(), e);
        }
    }

    @Scheduled(fixedDelayString = "${theclub.mail.dispatcher.interval:PT5S}", initialDelayString = "PT10S")
    public void scheduled() {
        if (!properties.dispatcher().enabled()) {
            return;
        }
        try {
            dispatchOnce();
        } catch (RuntimeException e) {
            log.error("Email dispatch run failed: {}", e.getMessage(), e);
        }
    }

    /**
     * One dispatch run.
     *
     * @return number of emails delivered.
     */
    public int dispatchOnce() {
        int released = outbox.releaseStuck();
        if (released > 0) {
            log.warn("Re-queued {} email(s) left SENDING by a stopped dispatcher", released);
        }
        List<EmailOutbox> batch = outbox.claimBatch(properties.dispatcher().batchSize());
        int delivered = 0;
        for (EmailOutbox email : batch) {
            if (deliver(email)) {
                delivered++;
            }
        }
        metrics.pending(outbox.pendingCount());
        if (!batch.isEmpty()) {
            log.info("Email dispatch: {} delivered, {} not delivered", delivered, batch.size() - delivered);
        }
        return delivered;
    }

    private boolean deliver(EmailOutbox email) {
        if (suppressions.existsByEmail(email.getRecipient().toLowerCase(Locale.ROOT))) {
            outbox.markFailed(email.getId(), provider.name(), "Recipient is suppressed after a hard bounce", false);
            metrics.failed(provider.name(), "suppressed");
            return false;
        }
        try {
            String messageId = provider.send(new OutgoingEmail(from.getAddress(), from.getPersonal(),
                    properties.replyTo(), email.getRecipient(), email.getSubject(), email.getHtmlBody(),
                    email.getTextBody(), outbox.attachmentsOf(email), email.getId().toString()));
            outbox.markSent(email.getId(), provider.name(), messageId);
            metrics.sent(provider.name(), email.getTemplate());
            return true;
        } catch (EmailDeliveryException e) {
            fail(email, e.getMessage(), e.isRetryable());
        } catch (RuntimeException e) {
            fail(email, "Unexpected error: " + e.getMessage(), true);
        }
        return false;
    }

    private void fail(EmailOutbox email, String error, boolean retryable) {
        boolean failedForGood = outbox.markFailed(email.getId(), provider.name(), error, retryable);
        metrics.failed(provider.name(), !retryable ? "permanent" : failedForGood ? "exhausted" : "retry");
        if (!failedForGood) {
            log.info("Email {} attempt {} failed, will retry: {}", email.getId(), email.getAttempts(), error);
        }
    }
}

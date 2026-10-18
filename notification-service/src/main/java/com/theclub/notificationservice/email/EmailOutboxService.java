package com.theclub.notificationservice.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.common.web.ApiException;
import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailAttachment;
import com.theclub.notificationservice.dto.EmailRequest;
import com.theclub.notificationservice.model.EmailOutbox;
import com.theclub.notificationservice.model.EmailStatus;
import com.theclub.notificationservice.repository.EmailOutboxRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The email outbox: rows are added in the caller's transaction and claimed by dispatchers with
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}, so several notification-service replicas can dispatch in parallel
 * without ever claiming the same email.
 */
@Service
public class EmailOutboxService {

    /** Wait before attempt n+1 after n failed attempts (the last value repeats). */
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(6));

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository outbox;
    private final EmailComposer composer;
    private final MailProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final String claimSql;

    @PersistenceContext
    private EntityManager entityManager;

    public EmailOutboxService(EmailOutboxRepository outbox, EmailComposer composer, MailProperties properties,
                              ObjectMapper json, Clock clock, DataSource dataSource) {
        this.outbox = outbox;
        this.composer = composer;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        this.claimSql = "SELECT id FROM email_outbox WHERE status = 'PENDING' AND next_attempt_at <= ?1 "
                + "ORDER BY next_attempt_at LIMIT ?2 FOR UPDATE" + (supportsSkipLocked(dataSource) ? " SKIP LOCKED" : "");
    }

    /** MySQL 8 and PostgreSQL support SKIP LOCKED; H2 (tests) only plain FOR UPDATE, which is fine single-node. */
    private static boolean supportsSkipLocked(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
            return product.contains("mysql") || product.contains("postgres") || product.contains("mariadb");
        } catch (SQLException e) {
            return false;
        }
    }

    /** Renders and queues an email; joins the caller's transaction so it commits with the notification. */
    @Transactional(propagation = Propagation.MANDATORY)
    public EmailOutbox enqueue(EmailRequest request) {
        EmailComposer.Composed composed = composer.compose(request);
        EmailOutbox row = new EmailOutbox(composed.recipient(), composed.subject(), composed.template(),
                composed.html(), composed.text(), attachmentsJson(request.attachments()), now());
        return outbox.save(row);
    }

    /** Claims up to {@code limit} due emails and marks them SENDING (attempt counted). */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<EmailOutbox> claimBatch(int limit) {
        List<Object> ids = entityManager.createNativeQuery(claimSql)
                .setParameter(1, now())
                .setParameter(2, limit)
                .getResultList();
        LocalDateTime now = now();
        return ids.stream().map(id -> UUID.fromString(id.toString())).map(id -> {
            EmailOutbox row = outbox.findById(id).orElseThrow();
            row.setStatus(EmailStatus.SENDING);
            row.setClaimedAt(now);
            row.setAttempts(row.getAttempts() + 1);
            return row;
        }).toList();
    }

    @Transactional
    public void markSent(UUID id, String provider, String providerMessageId) {
        outbox.findById(id).ifPresent(row -> {
            row.setStatus(EmailStatus.SENT);
            row.setProvider(provider);
            row.setProviderMessageId(providerMessageId);
            row.setSentAt(now());
            row.setClaimedAt(null);
            row.setLastError(null);
        });
    }

    /**
     * Records a failed attempt: schedules a retry with exponential backoff, or marks the email FAILED when the
     * error is permanent or attempts are exhausted.
     *
     * @return true when the email is now FAILED for good.
     */
    @Transactional
    public boolean markFailed(UUID id, String provider, String error, boolean retryable) {
        EmailOutbox row = outbox.findById(id).orElse(null);
        if (row == null) {
            return false;
        }
        row.setProvider(provider);
        row.setLastError(error);
        row.setClaimedAt(null);
        if (!retryable || row.getAttempts() >= properties.dispatcher().maxAttempts()) {
            row.setStatus(EmailStatus.FAILED);
            log.warn("Email {} (template {}) FAILED after {} attempt(s): {}", row.getId(), row.getTemplate(),
                    row.getAttempts(), error);
            return true;
        }
        row.setStatus(EmailStatus.PENDING);
        row.setNextAttemptAt(now().plus(backoff(row.getAttempts())));
        return false;
    }

    static Duration backoff(int failedAttempts) {
        return BACKOFF.get(Math.min(Math.max(failedAttempts, 1), BACKOFF.size()) - 1);
    }

    @Transactional
    public int releaseStuck() {
        return outbox.releaseStuck(now().minus(properties.dispatcher().stuckAfter()));
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return outbox.countByStatus(EmailStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Page<EmailOutbox> list(EmailStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest("page must be >= 0 and size between 1 and 100");
        }
        PageRequest pageable = PageRequest.of(page, size);
        return status == null ? outbox.findAllByOrderByCreatedAtDesc(pageable)
                : outbox.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /** Admin "send again": a FAILED email gets a fresh set of attempts. */
    @Transactional
    public EmailOutbox retry(UUID id) {
        EmailOutbox row = outbox.findById(id).orElseThrow(() -> ApiException.notFound("Email not found"));
        if (row.getStatus() != EmailStatus.FAILED) {
            throw ApiException.conflict("Only FAILED emails can be retried (this one is " + row.getStatus() + ")");
        }
        row.setStatus(EmailStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(now());
        row.setLastError(null);
        return row;
    }

    List<EmailAttachment> attachmentsOf(EmailOutbox row) {
        if (row.getAttachments() == null || row.getAttachments().isBlank()) {
            return List.of();
        }
        try {
            return List.of(json.readValue(row.getAttachments(), EmailAttachment[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt attachments on email " + row.getId(), e);
        }
    }

    private String attachmentsJson(List<EmailAttachment> attachments) {
        if (attachments.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(attachments);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Attachments could not be stored", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}

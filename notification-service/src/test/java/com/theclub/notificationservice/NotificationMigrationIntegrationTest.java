package com.theclub.notificationservice;

import com.theclub.notificationservice.dto.EmailRequest;
import com.theclub.notificationservice.email.EmailDispatcher;
import com.theclub.notificationservice.email.EmailOutboxService;
import com.theclub.notificationservice.model.EmailStatus;
import com.theclub.notificationservice.repository.EmailOutboxRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the Flyway migrations to a real MySQL 8.4 and lets Hibernate validate the entities against the result.
 * Runs wherever Docker is available (CI); skipped otherwise.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class NotificationMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired
    Flyway flyway;
    @Autowired
    EmailOutboxService emailOutbox;
    @Autowired
    EmailOutboxRepository outboxRows;
    @Autowired
    EmailDispatcher dispatcher;
    @Autowired
    TransactionTemplate tx;

    @Test
    void migrationsApplyAndMatchTheEntities() {
        // The context only starts if ddl-auto=validate accepted the migrated schema
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void emailOutboxClaimsWithSkipLockedOnMySql() {
        UUID id = tx.execute(status -> emailOutbox.enqueue(new EmailRequest("asha@example.com", null, null,
                "password-changed", Map.of("name", "Asha"), List.of())).getId());

        // the claim query uses FOR UPDATE SKIP LOCKED on MySQL; this proves the SQL is valid there
        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        assertThat(outboxRows.findById(id).orElseThrow().getStatus()).isEqualTo(EmailStatus.SENT);
    }
}

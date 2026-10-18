package com.theclub.userservice.config;

import com.theclub.userservice.model.Role;
import com.theclub.userservice.model.User;
import com.theclub.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Creates the first platform admin from ADMIN_EMAIL / ADMIN_PASSWORD so owner verification requests can be reviewed.
 * Safe when several replicas start at once: the loser of the race sees the unique-email conflict and moves on.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          PlatformTransactionManager transactionManager,
                          @Value("${theclub.admin.email:}") String email,
                          @Value("${theclub.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            transaction.executeWithoutResult(status -> ensureAdmin(normalized));
        } catch (DataIntegrityViolationException e) {
            // another replica inserted the admin between our lookup and our commit
            log.info("Bootstrap admin {} was created by another instance", normalized);
        }
    }

    private void ensureAdmin(String normalized) {
        users.findByEmail(normalized).ifPresentOrElse(existing -> existing.setEmailVerified(true), () -> {
            User admin = new User("Admin", normalized, passwordEncoder.encode(password), EnumSet.of(Role.USER, Role.ADMIN));
            admin.setEmailVerified(true);
            users.saveAndFlush(admin);
            log.info("Created bootstrap admin {}", normalized);
        });
    }
}

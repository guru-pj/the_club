package com.theclub.userservice.config;

import com.theclub.userservice.model.Role;
import com.theclub.userservice.model.User;
import com.theclub.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Creates the first platform admin from ADMIN_EMAIL / ADMIN_PASSWORD so owner verification requests can be reviewed.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${theclub.admin.email:}") String email,
                          @Value("${theclub.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(normalized).ifPresentOrElse(existing -> existing.setEmailVerified(true), () -> {
            User admin = new User("Admin", normalized, passwordEncoder.encode(password), EnumSet.of(Role.USER, Role.ADMIN));
            admin.setEmailVerified(true);
            users.save(admin);
            log.info("Created bootstrap admin {}", normalized);
        });
    }
}

package com.theclub.userservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.userservice.model.TokenPurpose;
import com.theclub.userservice.model.User;
import com.theclub.userservice.model.UserToken;
import com.theclub.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Email verification, password reset and password change. Request endpoints never reveal whether
 * an account exists.
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final AccountTokens tokens;
    private final AccountMailer mailer;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository users, AccountTokens tokens, AccountMailer mailer,
                          PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    /** Sends the verification email for a newly registered user (inside the registration transaction). */
    @Transactional
    public void startEmailVerification(User user) {
        tokens.issue(user.getId(), TokenPurpose.EMAIL_VERIFICATION).ifPresent(t -> mailer.sendVerification(user, t));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UserToken token = tokens.find(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        User user = users.findById(token.getUserId()).orElseThrow(() -> ApiException.badRequest(AccountTokens.INVALID));
        tokens.markUsed(token);
        user.setEmailVerified(true);
    }

    @Transactional
    public void resendVerification(String email) {
        findByEmail(email)
                .filter(u -> u.isActive() && !u.isEmailVerified())
                .ifPresent(this::startEmailVerification);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        findByEmail(email)
                .filter(User::isActive)
                .ifPresent(user -> tokens.issue(user.getId(), TokenPurpose.PASSWORD_RESET)
                        .ifPresent(t -> mailer.sendPasswordReset(user, t)));
    }

    /**
     * The password is checked before the token is consumed, so a rejected password doesn't burn the link.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UserToken token = tokens.find(rawToken, TokenPurpose.PASSWORD_RESET);
        User user = users.findById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.badRequest(AccountTokens.INVALID));
        passwordPolicy.check(newPassword, user.getEmail(), "newPassword");
        tokens.markUsed(token);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // receiving the link proves control of the inbox
        user.setEmailVerified(true);
        mailer.revokeSessions(user.getId());
        mailer.sendPasswordChanged(user);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Validation failed").withFieldError("currentPassword", "is incorrect");
        }
        if (currentPassword.equals(newPassword)) {
            throw ApiException.badRequest("Validation failed")
                    .withFieldError("newPassword", "must be different from your current password");
        }
        passwordPolicy.check(newPassword, user.getEmail(), "newPassword");
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        mailer.revokeSessions(user.getId());
        mailer.sendPasswordChanged(user);
    }

    private Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT));
    }
}

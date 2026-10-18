package com.theclub.userservice.config;

import com.theclub.userservice.model.User;
import com.theclub.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);

    @Test
    void createsTheAdminWhenMissing() {
        when(users.findByEmail("admin@theclub.local")).thenReturn(Optional.empty());
        when(encoder.encode("secret-pass")).thenReturn("hash");

        new AdminBootstrap(users, encoder, tx, " Admin@TheClub.local ", "secret-pass").run(null);

        verify(users).saveAndFlush(any(User.class));
    }

    @Test
    void anotherReplicaWinningTheRaceIsNotAStartupFailure() {
        when(users.findByEmail("admin@theclub.local")).thenReturn(Optional.empty());
        when(encoder.encode("secret-pass")).thenReturn("hash");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatCode(() -> new AdminBootstrap(users, encoder, tx, "admin@theclub.local", "secret-pass").run(null))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNothingWithoutCredentials() {
        new AdminBootstrap(users, encoder, tx, "", "").run(null);

        verify(users, org.mockito.Mockito.never()).findByEmail(any());
    }
}

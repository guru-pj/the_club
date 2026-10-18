package com.theclub.userservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.userservice.dto.RegisterUserRequest;
import com.theclub.userservice.model.Role;
import com.theclub.userservice.model.User;
import com.theclub.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository users;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AccountService accounts;
    @org.mockito.Spy
    PasswordPolicy passwordPolicy = new PasswordPolicy();
    @InjectMocks
    UserService userService;

    @Test
    void registerNormalizesEmailHashesPasswordAndGrantsUserRole() {
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng-pass!")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.register(new RegisterUserRequest(" Asha ", " Asha@Example.com ", "Str0ng-pass!"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("asha@example.com");
        assertThat(user.getName()).isEqualTo("Asha");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getRoles()).containsExactly(Role.USER);
        assertThat(user.isEmailVerified()).isFalse();
        verify(accounts).startEmailVerification(user);
    }

    @Test
    void registerRejectsCommonPasswords() {
        when(users.existsByEmail("asha@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.register(new RegisterUserRequest("Asha", "asha@example.com", "password123")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getFieldErrors()).containsEntry("password", "This password is too common"));
        verify(users, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new RegisterUserRequest("Asha", "asha@example.com", "Str0ng-pass!")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void cannotRemoveBaseUserRole() {
        assertThatThrownBy(() -> userService.removeRole(java.util.UUID.randomUUID(), Role.USER))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void addRoleOnInactiveUserIsNotFound() {
        User user = new User("A", "a@b.com", "h", EnumSet.of(Role.USER));
        user.setActive(false);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.addRole(user.getId(), Role.CLUB_OWNER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}

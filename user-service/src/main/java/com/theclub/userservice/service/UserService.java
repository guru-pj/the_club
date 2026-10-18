package com.theclub.userservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.userservice.dto.RegisterUserRequest;
import com.theclub.userservice.dto.UpdateUserRequest;
import com.theclub.userservice.model.Role;
import com.theclub.userservice.model.User;
import com.theclub.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AccountService accounts;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       AccountService accounts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.accounts = accounts;
    }

    @Transactional
    public User register(RegisterUserRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        passwordPolicy.check(request.password(), email, "password");
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password()),
                EnumSet.of(Role.USER)));
        accounts.startEmailVerification(user);
        return user;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional(readOnly = true)
    public User getActive(UUID id) {
        User user = get(id);
        if (!user.isActive()) {
            throw ApiException.notFound("User not found");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return users.findByEmail(normalizeEmail(email)).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional(readOnly = true)
    public List<User> getAll(Collection<UUID> ids) {
        return users.findAllById(ids);
    }

    @Transactional
    public User update(UUID id, UpdateUserRequest request) {
        User user = getActive(id);
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.city() != null) {
            user.setCity(request.city());
        }
        if (request.profilePicUrl() != null) {
            user.setProfilePicUrl(request.profilePicUrl());
        }
        return user;
    }

    @Transactional
    public void deactivate(UUID id) {
        get(id).setActive(false);
    }

    @Transactional
    public User addRole(UUID id, Role role) {
        User user = getActive(id);
        user.getRoles().add(role);
        return user;
    }

    @Transactional
    public User removeRole(UUID id, Role role) {
        if (role == Role.USER) {
            throw ApiException.badRequest("The USER role cannot be removed");
        }
        User user = get(id);
        user.getRoles().remove(role);
        return user;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

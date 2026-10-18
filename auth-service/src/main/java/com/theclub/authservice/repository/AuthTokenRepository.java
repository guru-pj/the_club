package com.theclub.authservice.repository;

import com.theclub.authservice.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByRefreshTokenHash(String refreshTokenHash);

    void deleteByRefreshTokenHash(String refreshTokenHash);
}

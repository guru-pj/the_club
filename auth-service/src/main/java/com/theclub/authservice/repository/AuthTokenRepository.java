package com.theclub.authservice.repository;

import com.theclub.authservice.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("delete from AuthToken t where t.refreshTokenHash = :hash")
    void deleteByRefreshTokenHash(@Param("hash") String refreshTokenHash);

    @Modifying
    @Query("delete from AuthToken t where t.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}

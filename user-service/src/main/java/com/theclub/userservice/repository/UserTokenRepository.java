package com.theclub.userservice.repository;

import com.theclub.userservice.model.TokenPurpose;
import com.theclub.userservice.model.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    Optional<UserToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, TokenPurpose purpose);

    /** Supersedes outstanding tokens so only the most recently emailed link works. */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateUnused(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose,
                         @Param("now") LocalDateTime now);
}

package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ClubFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubFollowRepository extends JpaRepository<ClubFollow, UUID> {

    Optional<ClubFollow> findByClubIdAndUserId(UUID clubId, UUID userId);

    boolean existsByClubIdAndUserId(UUID clubId, UUID userId);

    long countByClubId(UUID clubId);

    @Query("select f.userId from ClubFollow f where f.clubId = :clubId")
    List<UUID> findUserIdsByClubId(@Param("clubId") UUID clubId);

    @Query("select f.clubId from ClubFollow f where f.userId = :userId")
    List<UUID> findClubIdsByUserId(@Param("userId") UUID userId);
}

package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ClubMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, UUID> {

    Optional<ClubMembership> findByClubIdAndUserId(UUID clubId, UUID userId);

    long countByClubId(UUID clubId);

    Page<ClubMembership> findByClubIdOrderByJoinedAtAsc(UUID clubId, Pageable pageable);

    @Query("select m.userId from ClubMembership m where m.clubId = :clubId")
    List<UUID> findUserIdsByClubId(@Param("clubId") UUID clubId);

    /** Whether the two people are both subscribers (or the owner) of at least one club. */
    @Query("select count(m) > 0 from ClubMembership m where m.userId = :a "
            + "and m.clubId in (select n.clubId from ClubMembership n where n.userId = :b)")
    boolean shareAClub(@Param("a") UUID a, @Param("b") UUID b);
}

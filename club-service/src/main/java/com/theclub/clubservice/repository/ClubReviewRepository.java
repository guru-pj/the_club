package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ClubReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubReviewRepository extends JpaRepository<ClubReview, UUID> {

    Optional<ClubReview> findByClubIdAndUserId(UUID clubId, UUID userId);

    Page<ClubReview> findByClubIdOrderByUpdatedAtDesc(UUID clubId, Pageable pageable);

    /** Rating → number of reviews with it. */
    @Query("select r.rating, count(r) from ClubReview r where r.clubId = :clubId group by r.rating")
    List<Object[]> countByRating(@Param("clubId") UUID clubId);
}

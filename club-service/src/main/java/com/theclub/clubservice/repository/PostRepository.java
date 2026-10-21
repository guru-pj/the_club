package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findByClubIdOrderByCreatedAtDesc(UUID clubId, Pageable pageable);

    Optional<Post> findByIdAndClubId(UUID id, UUID clubId);

    boolean existsByClubIdAndSharedPostId(UUID clubId, UUID sharedPostId);

    boolean existsByClubIdAndSharedEventId(UUID clubId, UUID sharedEventId);
}

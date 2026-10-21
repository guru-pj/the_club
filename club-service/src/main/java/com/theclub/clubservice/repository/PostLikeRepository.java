package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    Optional<PostLike> findByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostId(UUID postId);

    /** Post ID → number of likes. */
    @Query("select l.postId, count(l) from PostLike l where l.postId in :postIds group by l.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<UUID> postIds);

    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<UUID> likedAmong(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}

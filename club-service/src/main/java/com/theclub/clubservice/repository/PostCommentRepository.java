package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    Page<PostComment> findByPostIdOrderByCreatedAtAscIdAsc(UUID postId, Pageable pageable);

    /** Post ID → number of comments. */
    @Query("select c.postId, count(c) from PostComment c where c.postId in :postIds group by c.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<UUID> postIds);
}

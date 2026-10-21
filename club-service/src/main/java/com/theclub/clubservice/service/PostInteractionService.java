package com.theclub.clubservice.service;

import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.CommentRequest;
import com.theclub.clubservice.dto.CommentResponse;
import com.theclub.clubservice.dto.LikeResponse;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.Post;
import com.theclub.clubservice.model.PostComment;
import com.theclub.clubservice.model.PostLike;
import com.theclub.clubservice.repository.PostCommentRepository;
import com.theclub.clubservice.repository.PostLikeRepository;
import com.theclub.common.observability.BusinessMetrics;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Likes (anyone signed in) and comments (the club's subscribers and followers) on feed posts. */
@Service
public class PostInteractionService {

    static final String TYPE_POST_COMMENT = "POST_COMMENT";
    static final int SNIPPET_LENGTH = 80;

    private final PostService postService;
    private final ClubService clubService;
    private final PostLikeRepository likes;
    private final PostCommentRepository comments;
    private final NotificationPublisher notifications;
    private final Clock clock;

    public PostInteractionService(PostService postService, ClubService clubService, PostLikeRepository likes,
                                  PostCommentRepository comments, NotificationPublisher notifications, Clock clock) {
        this.postService = postService;
        this.clubService = clubService;
        this.likes = likes;
        this.comments = comments;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Idempotent: liking twice keeps one like. Not transactional, so a concurrent duplicate is simply ignored. */
    public LikeResponse like(AuthenticatedUser caller, UUID clubId, UUID postId) {
        Post post = postService.get(clubId, postId);
        if (likes.findByPostIdAndUserId(post.getId(), caller.id()).isEmpty()) {
            try {
                likes.save(new PostLike(post.getId(), caller.id(), LocalDateTime.now(clock)));
                BusinessMetrics.increment("clubs.post_likes");
            } catch (DataIntegrityViolationException e) {
                // liked at the same moment from another device
            }
        }
        return new LikeResponse(true, likes.countByPostId(post.getId()));
    }

    @Transactional
    public LikeResponse unlike(AuthenticatedUser caller, UUID clubId, UUID postId) {
        Post post = postService.get(clubId, postId);
        likes.findByPostIdAndUserId(post.getId(), caller.id()).ifPresent(likes::delete);
        likes.flush();
        return new LikeResponse(false, likes.countByPostId(post.getId()));
    }

    /** Oldest first, like a conversation. */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> comments(UUID clubId, UUID postId, int page, int size) {
        Post post = postService.get(clubId, postId);
        Page<PostComment> result = comments.findByPostIdOrderByCreatedAtAscIdAsc(post.getId(),
                ClubService.pageRequest(page, size));
        Map<UUID, UserClient.UserSummary> authors = clubService.profilesOf(
                result.getContent().stream().map(PostComment::getAuthorId).collect(Collectors.toSet()));
        return PageResponse.of(result, c -> describe(c, authors.get(c.getAuthorId())));
    }

    @Transactional
    public CommentResponse comment(AuthenticatedUser caller, UUID clubId, UUID postId, CommentRequest request) {
        Club club = clubService.get(clubId);
        Post post = postService.get(clubId, postId);
        if (!ClubService.isOwnerOrAdmin(club, caller) && clubService.roleOf(clubId, caller.id()) == null
                && !clubService.isFollowing(clubId, caller.id())) {
            throw ApiException.forbidden("Follow or subscribe to the club to comment").withCode("AUDIENCE_ONLY");
        }
        String content = request.content().strip();
        PostComment saved = comments.save(new PostComment(post.getId(), caller.id(), content, LocalDateTime.now(clock)));
        BusinessMetrics.increment("clubs.post_comments");
        if (!post.getAuthorId().equals(caller.id())) {
            notifications.notifyAfterCommit(post.getAuthorId(), TYPE_POST_COMMENT,
                    caller.name() + " commented on “" + post.getTitle() + "”: " + snippet(content));
        }
        UserClient.UserSummary me = clubService.profilesOf(java.util.List.of(caller.id())).get(caller.id());
        return CommentResponse.of(saved, me == null ? caller.name() : me.name(), me == null ? null : me.profilePicUrl());
    }

    /** The comment's author, the club owner and admins may delete it. */
    @Transactional
    public void deleteComment(AuthenticatedUser caller, UUID clubId, UUID postId, UUID commentId) {
        Club club = clubService.get(clubId);
        Post post = postService.get(clubId, postId);
        PostComment comment = comments.findById(commentId).filter(c -> c.getPostId().equals(post.getId()))
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
        if (!comment.getAuthorId().equals(caller.id()) && !ClubService.isOwnerOrAdmin(club, caller)) {
            throw ApiException.forbidden("Only the author or the club owner can delete this comment");
        }
        comments.delete(comment);
    }

    private static CommentResponse describe(PostComment c, UserClient.UserSummary author) {
        return CommentResponse.of(c, author == null ? null : author.name(), author == null ? null : author.profilePicUrl());
    }

    private static String snippet(String text) {
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH - 1).stripTrailing() + "…";
    }
}

package com.theclub.clubservice.service;

import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.PostResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.Post;
import com.theclub.clubservice.repository.PostCommentRepository;
import com.theclub.clubservice.repository.PostLikeRepository;
import com.theclub.clubservice.repository.PostRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns posts into responses with their author, like and comment counts and, for reshares, what was shared. */
@Component
public class PostViews {

    static final int EXCERPT_LENGTH = 280;

    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final PostCommentRepository comments;
    private final ClubService clubService;

    public PostViews(PostRepository posts, PostLikeRepository likes, PostCommentRepository comments,
                     ClubService clubService) {
        this.posts = posts;
        this.likes = likes;
        this.comments = comments;
        this.clubService = clubService;
    }

    @Transactional(readOnly = true)
    public PostResponse describe(Post post, UUID callerId) {
        return describe(List.of(post), callerId).getFirst();
    }

    @Transactional(readOnly = true)
    public List<PostResponse> describe(List<Post> page, UUID callerId) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = page.stream().map(Post::getId).toList();
        Map<UUID, Long> likeCounts = counts(likes.countByPostIds(ids));
        Map<UUID, Long> commentCounts = counts(comments.countByPostIds(ids));
        Set<UUID> liked = callerId == null ? Set.of() : new HashSet<>(likes.likedAmong(callerId, ids));
        Map<UUID, UserClient.UserSummary> authors = clubService.profilesOf(
                page.stream().map(Post::getAuthorId).collect(Collectors.toSet()));
        Map<UUID, PostResponse.Shared> shared = shared(page);

        return page.stream().map(p -> {
            UserClient.UserSummary author = authors.get(p.getAuthorId());
            return PostResponse.of(p, new PostResponse.Extras(author == null ? null : author.name(),
                    author == null ? null : author.profilePicUrl(), likeCounts.getOrDefault(p.getId(), 0L),
                    commentCounts.getOrDefault(p.getId(), 0L), liked.contains(p.getId()), shared.get(p.getId())));
        }).toList();
    }

    private Map<UUID, PostResponse.Shared> shared(List<Post> page) {
        List<UUID> originalIds = page.stream().map(Post::getSharedPostId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, Post> originals = originalIds.isEmpty() ? Map.of()
                : posts.findAllById(originalIds).stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        Set<UUID> clubIds = new HashSet<>();
        originals.values().forEach(o -> clubIds.add(o.getClubId()));
        page.stream().map(Post::getSharedEventClubId).filter(Objects::nonNull).forEach(clubIds::add);
        Map<UUID, Club> clubs = clubIds.isEmpty() ? Map.of()
                : clubService.getAll(clubIds).stream().collect(Collectors.toMap(Club::getId, Function.identity()));

        Map<UUID, PostResponse.Shared> result = new HashMap<>();
        for (Post p : page) {
            if (p.getSharedPostId() != null) {
                Post original = originals.get(p.getSharedPostId());
                result.put(p.getId(), original == null
                        ? new PostResponse.Shared("POST", false, p.getSharedPostId(), null, null, null, null, null, null,
                        null, null, null)
                        : sharedPost(original, clubs.get(original.getClubId())));
            } else if (p.getSharedEventId() != null) {
                Club club = clubs.get(p.getSharedEventClubId());
                result.put(p.getId(), new PostResponse.Shared("EVENT", true, null, p.getSharedEventId(),
                        p.getSharedEventClubId(), club == null ? null : club.getName(),
                        club == null ? null : club.getLogoUrl(), p.getSharedEventTitle(), null, null,
                        p.getSharedEventStartTime(), null));
            }
        }
        return result;
    }

    private static PostResponse.Shared sharedPost(Post original, Club club) {
        return new PostResponse.Shared("POST", true, original.getId(), original.getEventId(), original.getClubId(),
                club == null ? null : club.getName(), club == null ? null : club.getLogoUrl(), original.getTitle(),
                excerpt(original.getContent()), firstPhoto(original), null, original.getCreatedAt());
    }

    static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flat = text.strip();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH - 1).stripTrailing() + "…";
    }

    static String firstPhoto(Post post) {
        return post.getPhotoUrls().isEmpty() ? null : post.getPhotoUrls().getFirst();
    }

    private static Map<UUID, Long> counts(Collection<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}

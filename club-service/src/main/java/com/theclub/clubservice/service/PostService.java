package com.theclub.clubservice.service;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.dto.CreatePostRequest;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.PostResponse;
import com.theclub.clubservice.dto.UpdatePostRequest;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.Post;
import com.theclub.clubservice.model.PostType;
import com.theclub.clubservice.repository.PostRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PostService {

    static final String STRAVA_SOURCE = "STRAVA";
    static final String ACTIVITY_LINK_ERROR = "Only the club owner's Strava activities can be linked";

    private final PostRepository posts;
    private final ClubService clubService;
    private final ActivityClient activityClient;
    private final EventClient eventClient;
    private final NotificationPublisher notifications;

    public PostService(PostRepository posts, ClubService clubService, ActivityClient activityClient,
                       EventClient eventClient, NotificationPublisher notifications) {
        this.posts = posts;
        this.clubService = clubService;
        this.activityClient = activityClient;
        this.eventClient = eventClient;
        this.notifications = notifications;
    }

    @Transactional
    public Post create(AuthenticatedUser caller, UUID clubId, CreatePostRequest request) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);

        PostType type = request.type() == null ? PostType.ANNOUNCEMENT : request.type();
        Post post = new Post(clubId, caller.id(), type, request.title().trim(), request.content());
        if (request.photoUrls() != null) {
            post.getPhotoUrls().addAll(request.photoUrls());
        }
        if (type == PostType.EVENT_RECAP) {
            if (request.eventId() == null) {
                throw ApiException.badRequest("eventId is required for an event recap");
            }
            requireEventOfClub(request.eventId(), clubId);
        }
        // A plain announcement may still reference an event, as long as it is this club's.
        if (type != PostType.EVENT_RECAP && request.eventId() != null) {
            requireEventOfClub(request.eventId(), clubId);
        }
        post.setEventId(request.eventId());
        if (request.activityId() != null) {
            attachOwnerStravaActivity(post, club, request.activityId());
        }
        Post saved = posts.save(post);

        List<UUID> recipients = clubService.memberIds(clubId).stream()
                .filter(id -> !id.equals(caller.id()))
                .toList();
        notifications.notifyAfterCommit(recipients, "CLUB_POST", club.getName() + ": " + saved.getTitle());
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> feed(UUID clubId, int page, int size) {
        clubService.get(clubId);
        return PageResponse.of(posts.findByClubIdOrderByCreatedAtDesc(clubId, ClubService.pageRequest(page, size)),
                PostResponse::of);
    }

    @Transactional(readOnly = true)
    public Post get(UUID clubId, UUID postId) {
        return posts.findByIdAndClubId(postId, clubId).orElseThrow(() -> ApiException.notFound("Post not found"));
    }

    @Transactional
    public Post update(AuthenticatedUser caller, UUID clubId, UUID postId, UpdatePostRequest request) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        Post post = get(clubId, postId);
        if (request.title() != null) {
            post.setTitle(request.title().trim());
        }
        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.photoUrls() != null) {
            post.getPhotoUrls().clear();
            post.getPhotoUrls().addAll(request.photoUrls());
        }
        return post;
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID clubId, UUID postId) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        posts.delete(get(clubId, postId));
    }

    private void requireEventOfClub(UUID eventId, UUID clubId) {
        EventClient.EventSummary event;
        try {
            event = eventClient.get(eventId);
        } catch (FeignException.NotFound e) {
            throw ApiException.badRequest("Event not found for this club");
        }
        if (event == null || !clubId.equals(event.clubId())) {
            throw ApiException.badRequest("Event not found for this club");
        }
    }

    private void attachOwnerStravaActivity(Post post, Club club, UUID activityId) {
        ActivityClient.ActivityDetails activity;
        try {
            activity = activityClient.get(activityId);
        } catch (FeignException.NotFound e) {
            throw ApiException.badRequest(ACTIVITY_LINK_ERROR);
        }
        if (activity == null || !club.getOwnerId().equals(activity.userId())
                || !STRAVA_SOURCE.equals(activity.source())) {
            throw ApiException.badRequest(ACTIVITY_LINK_ERROR);
        }
        post.setActivityId(activity.id());
        post.setActivityTitle(activity.title());
        post.setActivityType(activity.type());
        post.setActivityDistanceKm(activity.distance());
        post.setActivityDurationSeconds(activity.duration());
        post.setActivityStartTime(activity.startTime());
    }
}

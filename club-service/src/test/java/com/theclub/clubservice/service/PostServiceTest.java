package com.theclub.clubservice.service;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.dto.CreatePostRequest;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.Post;
import com.theclub.clubservice.model.PostType;
import com.theclub.clubservice.repository.PostRepository;
import com.theclub.common.media.MediaProperties;
import com.theclub.common.media.MediaStore;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceTest {

    @Mock
    PostRepository posts;
    @Mock
    ClubService clubService;
    @Mock
    ActivityClient activityClient;
    @Mock
    EventClient eventClient;
    @Mock
    NotificationPublisher notifications;
    @Mock
    PostViews views;
    @Mock
    MediaStore media;
    @Mock
    MediaProperties mediaProperties;
    @InjectMocks
    PostService postService;

    private final UUID ownerId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(ownerId, "o@example.com", "Owner", Set.of(Roles.USER, Roles.CLUB_OWNER));
    private Club club;

    @BeforeEach
    void setUp() {
        club = new Club("Chennai Runners", null, null, null, null, ownerId);
        when(clubService.get(club.getId())).thenReturn(club);
        when(posts.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaProperties.publicBaseUrl()).thenReturn("/media");
    }

    private static CreatePostRequest request(PostType type, UUID eventId, UUID activityId) {
        return new CreatePostRequest(type, "Sunday recap", "Great run!", List.of("https://img/1.jpg"), eventId, activityId);
    }

    private ActivityClient.ActivityDetails activity(UUID userId, String source) {
        return new ActivityClient.ActivityDetails(UUID.randomUUID(), userId, "Morning Run", "RUN", 10.5, 3600, 500,
                LocalDateTime.of(2026, 10, 18, 6, 0), LocalDateTime.of(2026, 10, 18, 7, 0), source, "123");
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/x", new HashMap<>(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, null);
    }

    @Test
    void onlyOwnerMayPost() {
        AuthenticatedUser member = new AuthenticatedUser(UUID.randomUUID(), "m@example.com", "M", Set.of(Roles.USER, Roles.CLUB_OWNER));

        assertThatThrownBy(() -> postService.create(member, club.getId(), request(PostType.ANNOUNCEMENT, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(posts, never()).save(any());
    }

    @Test
    void announcementNotifiesSubscribersAndFollowersExceptTheAuthor() {
        UUID memberId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        // audienceIds is a set: someone who both subscribes and follows appears once
        when(clubService.audienceIds(club.getId())).thenReturn(new LinkedHashSet<>(List.of(ownerId, memberId, followerId)));

        Post post = postService.create(owner, club.getId(), request(null, null, null));

        assertThat(post.getType()).isEqualTo(PostType.ANNOUNCEMENT);
        assertThat(post.getPhotoUrls()).containsExactly("https://img/1.jpg");
        verify(notifications).notifyAfterCommit(eq(List.of(memberId, followerId)), eq("CLUB_POST"),
                eq("Chennai Runners: Sunday recap"));
    }

    @Test
    void ownerStravaActivityIsSnapshotted() {
        var activity = activity(ownerId, "STRAVA");
        when(activityClient.get(activity.id())).thenReturn(activity);

        Post post = postService.create(owner, club.getId(), request(PostType.ANNOUNCEMENT, null, activity.id()));

        assertThat(post.getActivityId()).isEqualTo(activity.id());
        assertThat(post.getActivityDistanceKm()).isEqualTo(10.5);
        assertThat(post.getActivityDurationSeconds()).isEqualTo(3600);
        assertThat(post.getActivityTitle()).isEqualTo("Morning Run");
    }

    @Test
    void manualActivityCannotBeLinked() {
        var activity = activity(ownerId, "MANUAL");
        when(activityClient.get(activity.id())).thenReturn(activity);

        assertThatThrownBy(() -> postService.create(owner, club.getId(), request(PostType.ANNOUNCEMENT, null, activity.id())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getMessage()).isEqualTo(PostService.ACTIVITY_LINK_ERROR));
    }

    @Test
    void someoneElsesStravaActivityCannotBeLinked() {
        var activity = activity(UUID.randomUUID(), "STRAVA");
        when(activityClient.get(activity.id())).thenReturn(activity);

        assertThatThrownBy(() -> postService.create(owner, club.getId(), request(PostType.ANNOUNCEMENT, null, activity.id())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void recapRequiresEventId() {
        assertThatThrownBy(() -> postService.create(owner, club.getId(), request(PostType.EVENT_RECAP, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void recapEventMustBelongToClub() {
        UUID eventId = UUID.randomUUID();
        when(eventClient.get(eventId)).thenReturn(new EventClient.EventSummary(eventId, UUID.randomUUID(), "Other", null, "SCHEDULED"));

        assertThatThrownBy(() -> postService.create(owner, club.getId(), request(PostType.EVENT_RECAP, eventId, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getMessage()).isEqualTo("Event not found for this club"));
    }

    @Test
    void unknownRecapEventIsBadRequest() {
        UUID eventId = UUID.randomUUID();
        when(eventClient.get(eventId)).thenThrow(notFound());

        assertThatThrownBy(() -> postService.create(owner, club.getId(), request(PostType.EVENT_RECAP, eventId, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void validRecapIsSaved() {
        UUID eventId = UUID.randomUUID();
        when(eventClient.get(eventId)).thenReturn(new EventClient.EventSummary(eventId, club.getId(), "Sunday Long Run", null, "COMPLETED"));
        when(clubService.memberIds(club.getId())).thenReturn(List.of(ownerId));

        Post post = postService.create(owner, club.getId(), request(PostType.EVENT_RECAP, eventId, null));

        assertThat(post.getEventId()).isEqualTo(eventId);
        verify(notifications).notifyAfterCommit(anyCollection(), eq("CLUB_POST"), any());
    }
}

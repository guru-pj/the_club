package com.theclub.clubservice.service;

import com.theclub.common.media.MediaProperties;
import com.theclub.common.media.MediaStore;
import com.theclub.common.observability.BusinessMetrics;
import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.dto.CreatePostRequest;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.PostPreview;
import com.theclub.clubservice.dto.PostResponse;
import com.theclub.clubservice.dto.ResharePostRequest;
import com.theclub.clubservice.dto.UpdatePostRequest;
import com.theclub.clubservice.dto.UploadedImage;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.MembershipRole;
import com.theclub.clubservice.model.Post;
import com.theclub.clubservice.model.PostType;
import com.theclub.clubservice.repository.PostRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The club feed. The owner posts announcements and event recaps and reshares other clubs' posts and events;
 * subscribers write posts of their own. Likes and comments are in {@link PostInteractionService}.
 */
@Service
public class PostService {

    static final String STRAVA_SOURCE = "STRAVA";
    static final String ACTIVITY_LINK_ERROR = "Only the club owner's Strava activities can be linked";
    static final String OWN_ACTIVITY_LINK_ERROR = "Only your own Strava activities can be linked";
    static final String TYPE_CLUB_POST = "CLUB_POST";
    static final String TYPE_MEMBER_POST = "CLUB_MEMBER_POST";
    static final String TYPE_POST_RESHARED = "POST_RESHARED";

    private final PostRepository posts;
    private final ClubService clubService;
    private final ActivityClient activityClient;
    private final EventClient eventClient;
    private final NotificationPublisher notifications;
    private final PostViews views;
    private final MediaStore media;
    private final MediaProperties mediaProperties;

    public PostService(PostRepository posts, ClubService clubService, ActivityClient activityClient,
                       EventClient eventClient, NotificationPublisher notifications, PostViews views, MediaStore media,
                       MediaProperties mediaProperties) {
        this.posts = posts;
        this.clubService = clubService;
        this.activityClient = activityClient;
        this.eventClient = eventClient;
        this.notifications = notifications;
        this.views = views;
        this.media = media;
        this.mediaProperties = mediaProperties;
    }

    @Transactional
    public Post create(AuthenticatedUser caller, UUID clubId, CreatePostRequest request) {
        Club club = clubService.get(clubId);
        boolean manager = ClubService.isOwnerOrAdmin(club, caller);
        if (!manager) {
            requireSubscriber(clubId, caller);
            if (request.type() != null && request.type() != PostType.POST) {
                throw ApiException.forbidden("Only the club owner can post announcements and event recaps");
            }
        }
        PostType type = manager ? (request.type() == null ? PostType.ANNOUNCEMENT : request.type()) : PostType.POST;
        if (type == PostType.RESHARE) {
            throw ApiException.badRequest("Reshare posts and events with /posts/reshare");
        }

        Post post = new Post(clubId, caller.id(), type, request.title().trim(), request.content());
        if (request.photoUrls() != null) {
            requireOwnUploads(clubId, request.photoUrls());
            post.getPhotoUrls().addAll(request.photoUrls());
        }
        if (type == PostType.EVENT_RECAP) {
            if (request.eventId() == null) {
                throw ApiException.badRequest("eventId is required for an event recap");
            }
            requireEventOfClub(request.eventId(), clubId);
        }
        // Any other post may still reference an event, as long as it is this club's.
        if (type != PostType.EVENT_RECAP && request.eventId() != null) {
            requireEventOfClub(request.eventId(), clubId);
        }
        post.setEventId(request.eventId());
        if (request.activityId() != null) {
            // the owner (or an admin posting for the club) links the owner's runs; a subscriber links their own
            attachStravaActivity(post, manager ? club.getOwnerId() : caller.id(), request.activityId(),
                    manager ? ACTIVITY_LINK_ERROR : OWN_ACTIVITY_LINK_ERROR);
        }
        Post saved = posts.save(post);
        BusinessMetrics.increment("clubs.posts", "type", type.name().toLowerCase(Locale.ROOT),
                "strava_linked", String.valueOf(request.activityId() != null));

        if (manager) {
            // Subscribers and followers both get the club's updates
            notifyAudience(club, caller, saved);
        } else if (!club.getOwnerId().equals(caller.id())) {
            notifications.notifyAfterCommit(club.getOwnerId(), TYPE_MEMBER_POST,
                    caller.name() + " posted in " + club.getName() + ": " + saved.getTitle());
        }
        return saved;
    }

    /** Shares another club's post, or an event, into this club's feed. */
    @Transactional
    public Post reshare(AuthenticatedUser caller, UUID clubId, ResharePostRequest request) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        if ((request.postId() == null) == (request.eventId() == null)) {
            throw ApiException.badRequest("Give either postId or eventId");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        if (request.eventId() != null) {
            return reshareEvent(caller, club, request.eventId(), comment);
        }
        Post source = posts.findById(request.postId()).orElseThrow(() -> ApiException.notFound("Post not found"));
        // Resharing a reshare shares what it shared
        if (source.getSharedEventId() != null) {
            return reshareEvent(caller, club, source.getSharedEventId(), comment);
        }
        if (source.getSharedPostId() != null) {
            source = posts.findById(source.getSharedPostId())
                    .orElseThrow(() -> ApiException.notFound("The original post was deleted"));
        }
        if (source.getClubId().equals(clubId)) {
            throw ApiException.badRequest("This post is already in your club's feed");
        }
        if (posts.existsByClubIdAndSharedPostId(clubId, source.getId())) {
            throw ApiException.conflict("Your club already reshared this post").withCode("ALREADY_RESHARED");
        }
        Post post = new Post(clubId, caller.id(), PostType.RESHARE, source.getTitle(), comment);
        post.setSharedPostId(source.getId());
        Post saved = posts.save(post);
        BusinessMetrics.increment("clubs.posts", "type", "reshare", "strava_linked", "false");
        notifyAudience(club, caller, saved);
        if (!source.getAuthorId().equals(caller.id())) {
            notifications.notifyAfterCommit(source.getAuthorId(), TYPE_POST_RESHARED,
                    club.getName() + " reshared your post “" + saved.getTitle() + "”");
        }
        return saved;
    }

    private Post reshareEvent(AuthenticatedUser caller, Club club, UUID eventId, String comment) {
        EventClient.EventSummary event;
        try {
            event = eventClient.get(eventId);
        } catch (FeignException.NotFound e) {
            event = null;
        }
        if (event == null) {
            throw ApiException.notFound("Event not found");
        }
        if ("CANCELLED".equals(event.status())) {
            throw ApiException.badRequest("A cancelled event can't be reshared");
        }
        if (posts.existsByClubIdAndSharedEventId(club.getId(), eventId)) {
            throw ApiException.conflict("Your club already reshared this event").withCode("ALREADY_RESHARED");
        }
        String title = event.title() == null ? "Event" : event.title();
        Post post = new Post(club.getId(), caller.id(), PostType.RESHARE, title.length() > 150 ? title.substring(0, 150) : title,
                comment);
        post.setSharedEventId(event.id());
        post.setSharedEventClubId(event.clubId());
        post.setSharedEventTitle(post.getTitle());
        post.setSharedEventStartTime(event.startTime());
        Post saved = posts.save(post);
        BusinessMetrics.increment("clubs.posts", "type", "reshare", "strava_linked", "false");
        notifyAudience(club, caller, saved);
        return saved;
    }

    private void notifyAudience(Club club, AuthenticatedUser caller, Post post) {
        List<UUID> recipients = clubService.audienceIds(club.getId()).stream()
                .filter(id -> !id.equals(caller.id()))
                .toList();
        notifications.notifyAfterCommit(recipients, TYPE_CLUB_POST, club.getName() + ": " + post.getTitle());
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> feed(UUID clubId, int page, int size, UUID callerId) {
        clubService.get(clubId);
        Page<Post> result = posts.findByClubIdOrderByCreatedAtDesc(clubId, ClubService.pageRequest(page, size));
        return new PageResponse<>(views.describe(result.getContent(), callerId), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Post get(UUID clubId, UUID postId) {
        return posts.findByIdAndClubId(postId, clubId).orElseThrow(() -> ApiException.notFound("Post not found"));
    }

    @Transactional(readOnly = true)
    public PostResponse describe(UUID clubId, UUID postId, UUID callerId) {
        return views.describe(get(clubId, postId), callerId);
    }

    @Transactional(readOnly = true)
    public PostResponse describe(Post post, UUID callerId) {
        return views.describe(post, callerId);
    }

    /** For a shared link opened by someone who isn't signed in. */
    @Transactional(readOnly = true)
    public PostPreview preview(UUID clubId, UUID postId) {
        Post post = get(clubId, postId);
        Club club = clubService.get(clubId);
        PostResponse full = views.describe(post, null);
        String excerpt = PostViews.excerpt(post.getContent());
        String photo = PostViews.firstPhoto(post);
        if (full.shared() != null) {
            excerpt = excerpt != null ? excerpt : full.shared().excerpt();
            photo = photo != null ? photo : full.shared().photoUrl();
        }
        return new PostPreview(post.getId(), clubId, club.getName(), club.getLogoUrl(), post.getType(), post.getTitle(),
                excerpt, photo, full.authorName(), post.getCreatedAt());
    }

    @Transactional
    public Post update(AuthenticatedUser caller, UUID clubId, UUID postId, UpdatePostRequest request) {
        clubService.get(clubId);
        Post post = get(clubId, postId);
        if (!post.getAuthorId().equals(caller.id()) && !caller.isAdmin()) {
            throw ApiException.forbidden("Only the author can edit this post");
        }
        if (request.title() != null) {
            post.setTitle(request.title().trim());
        }
        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.photoUrls() != null) {
            requireOwnUploads(clubId, request.photoUrls());
            List<String> removed = post.getPhotoUrls().stream().filter(u -> !request.photoUrls().contains(u)).toList();
            post.getPhotoUrls().clear();
            post.getPhotoUrls().addAll(request.photoUrls());
            removed.forEach(media::deleteByUrl);
        }
        return post;
    }

    /** The author, the club owner and admins may delete a post. */
    @Transactional
    public void delete(AuthenticatedUser caller, UUID clubId, UUID postId) {
        Club club = clubService.get(clubId);
        Post post = get(clubId, postId);
        if (!post.getAuthorId().equals(caller.id()) && !ClubService.isOwnerOrAdmin(club, caller)) {
            throw ApiException.forbidden("Only the author or the club owner can delete this post");
        }
        List<String> photos = List.copyOf(post.getPhotoUrls());
        posts.delete(post);
        photos.forEach(media::deleteByUrl);
    }

    /** Uploads a photo for a post the caller is about to write in this club. */
    @Transactional(readOnly = true)
    public UploadedImage uploadPhoto(AuthenticatedUser caller, UUID clubId, MultipartFile file) {
        Club club = clubService.get(clubId);
        if (!ClubService.isOwnerOrAdmin(club, caller)) {
            requireSubscriber(clubId, caller);
        }
        return new UploadedImage(media.storeImage(photoFolder(clubId), file).url());
    }

    private void requireSubscriber(UUID clubId, AuthenticatedUser caller) {
        if (clubService.roleOf(clubId, caller.id()) != MembershipRole.MEMBER) {
            throw ApiException.forbidden("Subscribe to the club to post in its feed").withCode("SUBSCRIBERS_ONLY");
        }
    }

    private static String photoFolder(UUID clubId) {
        return "posts/" + clubId;
    }

    /** Uploaded images ("/media/…") must have been uploaded for this club; pasted http(s) links are fine. */
    private void requireOwnUploads(UUID clubId, List<String> urls) {
        String prefix = mediaProperties.publicBaseUrl() + "/" + photoFolder(clubId) + "/";
        for (String url : urls) {
            if (url.startsWith("/") && !url.startsWith(prefix)) {
                throw ApiException.badRequest("Upload photos for this club with POST /posts/photos");
            }
        }
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

    private void attachStravaActivity(Post post, UUID allowedOwner, UUID activityId, String error) {
        ActivityClient.ActivityDetails activity;
        try {
            activity = activityClient.get(activityId);
        } catch (FeignException.NotFound e) {
            throw ApiException.badRequest(error);
        }
        if (activity == null || !allowedOwner.equals(activity.userId()) || !STRAVA_SOURCE.equals(activity.source())) {
            throw ApiException.badRequest(error);
        }
        post.setActivityId(activity.id());
        post.setActivityTitle(activity.title());
        post.setActivityType(activity.type());
        post.setActivityDistanceKm(activity.distance());
        post.setActivityDurationSeconds(activity.duration());
        post.setActivityStartTime(activity.startTime());
    }
}

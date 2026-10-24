package com.theclub.activityservice.service;

import com.theclub.activityservice.dto.ActivityRequest;
import com.theclub.activityservice.dto.UserActivityStats;
import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.repository.ActivityRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Service
public class ActivityService {

    /** Bounds used instead of open-ended ranges so queries never bind null timestamps. */
    static final LocalDateTime MIN_TIME = LocalDateTime.of(1900, 1, 1, 0, 0);
    static final LocalDateTime MAX_TIME = LocalDateTime.of(9999, 12, 31, 0, 0);
    static final int MAX_PAGE_SIZE = 100;
    private static final Duration FUTURE_SKEW = Duration.ofMinutes(5);

    private final ActivityRepository activities;
    private final Clock clock;

    public ActivityService(ActivityRepository activities, Clock clock) {
        this.activities = activities;
        this.clock = clock;
    }

    @Transactional
    public Activity create(UUID userId, ActivityRequest request) {
        Activity activity = new Activity(userId, ActivitySource.MANUAL);
        apply(activity, request);
        return activities.save(activity);
    }

    @Transactional
    public Activity update(UUID id, AuthenticatedUser caller, ActivityRequest request) {
        Activity activity = get(id);
        if (!activity.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("You can only edit your own activities");
        }
        if (activity.getSource() != ActivitySource.MANUAL) {
            throw ApiException.badRequest("Strava activities are updated from Strava");
        }
        apply(activity, request);
        return activity;
    }

    @Transactional(readOnly = true)
    public Activity get(UUID id) {
        return activities.findById(id).orElseThrow(() -> ApiException.notFound("Activity not found"));
    }

    /**
     * The activity if it belongs to the caller (or the caller is an admin). Anyone else gets 404, so activity ids
     * of other users cannot be probed.
     */
    @Transactional(readOnly = true)
    public Activity getVisibleTo(UUID id, AuthenticatedUser caller) {
        Activity activity = get(id);
        if (!activity.getUserId().equals(caller.id()) && !caller.isAdmin()) {
            throw ApiException.notFound("Activity not found");
        }
        return activity;
    }

    @Transactional
    public void delete(UUID id, AuthenticatedUser caller) {
        Activity activity = get(id);
        if (!activity.getUserId().equals(caller.id()) && !caller.isAdmin()) {
            throw ApiException.forbidden("You can only delete your own activities");
        }
        activities.delete(activity);
    }

    @Transactional(readOnly = true)
    public Page<Activity> list(UUID userId, ActivitySource source, LocalDateTime from, LocalDateTime to,
                               int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "startTime"));
        LocalDateTime lower = from == null ? MIN_TIME : from;
        LocalDateTime upper = to == null ? MAX_TIME : to;
        if (source == null) {
            return activities.findByUserIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(userId, lower, upper, pageable);
        }
        return activities.findByUserIdAndSourceAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                userId, source, lower, upper, pageable);
    }

    @Transactional(readOnly = true)
    public List<UserActivityStats> stats(List<UUID> userIds, LocalDateTime from, LocalDateTime to) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return activities.aggregate(userIds, from == null ? MIN_TIME : from, to == null ? MAX_TIME : to);
    }

    private void apply(Activity activity, ActivityRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.startTime().isAfter(now.plus(FUTURE_SKEW))) {
            throw ApiException.badRequest("startTime cannot be in the future");
        }
        LocalDateTime endTime = request.endTime() != null
                ? request.endTime()
                : request.startTime().plusSeconds(request.duration());
        if (endTime.isBefore(request.startTime())) {
            throw ApiException.badRequest("endTime must not be before startTime");
        }
        activity.setTitle(request.title().trim());
        activity.setType(request.type());
        activity.setDistance(request.distance());
        activity.setDuration(request.duration());
        activity.setCalories(request.calories());
        activity.setStartTime(request.startTime());
        activity.setEndTime(endTime);
    }
}

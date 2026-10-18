package com.theclub.activityservice.service;

import com.theclub.activityservice.dto.ActivityRequest;
import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.ActivityType;
import com.theclub.activityservice.repository.ActivityRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 8, 0);

    @Mock
    ActivityRepository activities;

    ActivityService service;
    final UUID userId = UUID.randomUUID();
    final AuthenticatedUser owner = new AuthenticatedUser(userId, "a@b.com", "A", Set.of(Roles.USER));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ActivityService(activities, clock);
    }

    private static ActivityRequest request(LocalDateTime start, LocalDateTime end) {
        return new ActivityRequest(" Morning Run ", ActivityType.RUN, 5.2, 1800, 350, start, end);
    }

    @Test
    void createAssignsCallerAndDefaultsEndTime() {
        when(activities.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        Activity activity = service.create(userId, request(NOW.minusHours(1), null));

        assertThat(activity.getUserId()).isEqualTo(userId);
        assertThat(activity.getSource()).isEqualTo(ActivitySource.MANUAL);
        assertThat(activity.getTitle()).isEqualTo("Morning Run");
        assertThat(activity.getEndTime()).isEqualTo(NOW.minusHours(1).plusSeconds(1800));
    }

    @Test
    void createAllowsSmallClockSkewButRejectsFutureStart() {
        when(activities.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(userId, request(NOW.plusMinutes(4), null));

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusMinutes(10), null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createRejectsEndBeforeStart() {
        assertThatThrownBy(() -> service.create(userId, request(NOW.minusHours(1), NOW.minusHours(2))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void onlyOwnerCanUpdate() {
        Activity activity = new Activity(UUID.randomUUID(), ActivitySource.MANUAL);
        when(activities.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> service.update(activity.getId(), owner, request(NOW.minusHours(1), null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void stravaActivitiesCannotBeEditedManually() {
        Activity activity = new Activity(userId, ActivitySource.STRAVA);
        when(activities.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> service.update(activity.getId(), owner, request(NOW.minusHours(1), null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminCanDeleteOthersActivities() {
        Activity activity = new Activity(UUID.randomUUID(), ActivitySource.MANUAL);
        when(activities.findById(activity.getId())).thenReturn(Optional.of(activity));
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "x@y.com", "X", Set.of(Roles.USER, Roles.ADMIN));

        assertThatThrownBy(() -> service.delete(activity.getId(), owner)).isInstanceOf(ApiException.class);
        service.delete(activity.getId(), admin);

        verify(activities).delete(activity);
    }

    @Test
    void statsForNoUsersSkipsQuery() {
        assertThat(service.stats(List.of(), null, null)).isEmpty();
        verifyNoInteractions(activities);
    }
}

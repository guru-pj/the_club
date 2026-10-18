package com.theclub.activityservice.repository;

import com.theclub.activityservice.dto.UserActivityStats;
import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Page<Activity> findByUserIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            UUID userId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<Activity> findByUserIdAndSourceAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            UUID userId, ActivitySource source, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Optional<Activity> findBySourceAndExternalId(ActivitySource source, String externalId);

    @Query("""
            select new com.theclub.activityservice.dto.UserActivityStats(
                a.userId, sum(a.distance), sum(a.duration), count(a))
            from Activity a
            where a.userId in :userIds and a.startTime >= :from and a.startTime < :to
            group by a.userId
            """)
    List<UserActivityStats> aggregate(@Param("userIds") Collection<UUID> userIds,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);
}

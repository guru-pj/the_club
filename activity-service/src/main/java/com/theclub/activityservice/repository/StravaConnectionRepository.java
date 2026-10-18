package com.theclub.activityservice.repository;

import com.theclub.activityservice.model.StravaConnection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StravaConnectionRepository extends JpaRepository<StravaConnection, UUID> {

    Optional<StravaConnection> findByUserId(UUID userId);

    Optional<StravaConnection> findByAthleteId(long athleteId);

    /** Row lock so concurrent token refreshes (threads or replicas) never lose a rotated refresh token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StravaConnection c where c.id = :id")
    Optional<StravaConnection> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Marks a sync as running unless one already is (a stale marker older than {@code staleBefore} is taken over).
     *
     * @return 1 when this caller may sync
     */
    @Modifying
    @Query("""
            update StravaConnection c set c.syncStartedAt = :now
            where c.id = :id and (c.syncStartedAt is null or c.syncStartedAt < :staleBefore)
            """)
    int claimSync(@Param("id") UUID id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    @Modifying
    @Query("update StravaConnection c set c.syncStartedAt = null where c.id = :id")
    int releaseSync(@Param("id") UUID id);

    @Modifying
    @Query("update StravaConnection c set c.syncStartedAt = null, c.lastSyncedAt = :syncedAt where c.id = :id")
    int completeSync(@Param("id") UUID id, @Param("syncedAt") Instant syncedAt);
}

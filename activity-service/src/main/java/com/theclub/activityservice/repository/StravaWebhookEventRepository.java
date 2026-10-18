package com.theclub.activityservice.repository;

import com.theclub.activityservice.model.StravaWebhookEventRecord;
import com.theclub.activityservice.model.StravaWebhookEventRecord.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StravaWebhookEventRepository extends JpaRepository<StravaWebhookEventRecord, UUID> {

    boolean existsByObjectTypeAndObjectIdAndAspectTypeAndEventTime(String objectType, long objectId,
                                                                   String aspectType, long eventTime);

    /** Candidates: due PENDING events, plus PROCESSING ones whose worker lease expired (crashed replica). */
    @Query("""
            select e.id from StravaWebhookEventRecord e
            where (e.status = com.theclub.activityservice.model.StravaWebhookEventRecord.Status.PENDING
                       and e.nextAttemptAt <= :now)
               or (e.status = com.theclub.activityservice.model.StravaWebhookEventRecord.Status.PROCESSING
                       and e.leaseUntil < :now)
            order by e.receivedAt
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);

    /**
     * Atomically claims one event for this worker. Only one replica's UPDATE can match, so the event is
     * processed once even with several replicas polling the same table.
     *
     * @return 1 when claimed, 0 when another worker got it first
     */
    @Modifying
    @Query("""
            update StravaWebhookEventRecord e
            set e.status = com.theclub.activityservice.model.StravaWebhookEventRecord.Status.PROCESSING,
                e.leaseUntil = :leaseUntil
            where e.id = :id
              and ((e.status = com.theclub.activityservice.model.StravaWebhookEventRecord.Status.PENDING
                        and e.nextAttemptAt <= :now)
                or (e.status = com.theclub.activityservice.model.StravaWebhookEventRecord.Status.PROCESSING
                        and e.leaseUntil < :now))
            """)
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying
    @Query("delete from StravaWebhookEventRecord e where e.status in :statuses and e.receivedAt < :before")
    int deleteFinishedBefore(@Param("statuses") Collection<Status> statuses, @Param("before") Instant before);

    long countByStatus(Status status);
}

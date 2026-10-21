package com.theclub.eventservice.repository;

import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Serializes registrations for one event so capacity cannot be oversold. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    Page<Event> findByClubId(UUID clubId, Pageable pageable);

    Page<Event> findByClubIdAndStartTimeGreaterThanEqual(UUID clubId, LocalDateTime from, Pageable pageable);

    /** The upcoming-events feed: scheduled events of these clubs that haven't started, soonest first. */
    List<Event> findByClubIdInAndStatusAndStartTimeAfterOrderByStartTimeAsc(Collection<UUID> clubIds, EventStatus status,
                                                                          LocalDateTime after, Pageable pageable);
}

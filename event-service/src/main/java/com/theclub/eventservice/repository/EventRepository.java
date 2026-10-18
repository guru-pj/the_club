package com.theclub.eventservice.repository;

import com.theclub.eventservice.model.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Serializes registrations for one event so capacity cannot be oversold. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    Page<Event> findByClubId(UUID clubId, Pageable pageable);

    Page<Event> findByClubIdAndStartTimeGreaterThanEqual(UUID clubId, LocalDateTime from, Pageable pageable);
}

package com.theclub.eventservice.repository;

import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    Optional<Registration> findByEventIdAndUserId(UUID eventId, UUID userId);

    Optional<Registration> findByTicketCode(String ticketCode);

    List<Registration> findByUserIdOrderByRegisteredAtDesc(UUID userId);

    List<Registration> findByEventIdOrderByRegisteredAt(UUID eventId);

    List<Registration> findByEventIdAndStatusIn(UUID eventId, Collection<RegistrationStatus> statuses);

    long countByEventIdAndStatusIn(UUID eventId, Collection<RegistrationStatus> statuses);

    /** Event ID, status, number of registrations. */
    @Query("select r.eventId, r.status, count(r) from Registration r where r.eventId in :eventIds group by r.eventId, r.status")
    List<Object[]> countByEventAndStatus(@Param("eventIds") Collection<UUID> eventIds);

    /** Whether the user checked in at any event of the club. */
    @Query("""
            select count(r) > 0 from Registration r
            where r.userId = :userId and r.checkedInAt is not null
              and r.eventId in (select e.id from Event e where e.clubId = :clubId)
            """)
    boolean hasAttendedClub(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    /**
     * Spots taken by other users: confirmed/checked-in registrations plus payments still in their hold window.
     */
    @Query("""
            select count(r) from Registration r
            where r.eventId = :eventId and r.userId <> :excludedUserId
              and (r.status in (com.theclub.eventservice.model.RegistrationStatus.CONFIRMED,
                                com.theclub.eventservice.model.RegistrationStatus.CHECKED_IN)
                   or (r.status = com.theclub.eventservice.model.RegistrationStatus.PENDING_PAYMENT
                       and r.registeredAt >= :pendingSince))
            """)
    long countSpotsTaken(@Param("eventId") UUID eventId, @Param("excludedUserId") UUID excludedUserId,
                         @Param("pendingSince") LocalDateTime pendingSince);
}

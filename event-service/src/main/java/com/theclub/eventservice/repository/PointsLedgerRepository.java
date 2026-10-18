package com.theclub.eventservice.repository;

import com.theclub.eventservice.model.PointsLedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PointsLedgerRepository extends JpaRepository<PointsLedgerEntry, UUID> {

    boolean existsByUserIdAndEventId(UUID userId, UUID eventId);

    @Query("""
            select new com.theclub.eventservice.repository.PointsTotals(count(p), coalesce(sum(p.points), 0L))
            from PointsLedgerEntry p where p.userId = :userId
            """)
    PointsTotals totalsForUser(@Param("userId") UUID userId);

    @Query("""
            select new com.theclub.eventservice.repository.PointsTotals(count(p), coalesce(sum(p.points), 0L))
            from PointsLedgerEntry p where p.userId = :userId and p.clubId = :clubId
            """)
    PointsTotals totalsForUserInClub(@Param("userId") UUID userId, @Param("clubId") UUID clubId);

    @Query("""
            select new com.theclub.eventservice.repository.UserPointsAggregate(p.userId, sum(p.points), count(p))
            from PointsLedgerEntry p where p.clubId = :clubId
            group by p.userId
            order by count(p) desc, sum(p.points) desc
            """)
    List<UserPointsAggregate> participationByClub(@Param("clubId") UUID clubId);

    @Query("""
            select new com.theclub.eventservice.repository.UserPointsAggregate(p.userId, sum(p.points), count(p))
            from PointsLedgerEntry p where p.awardedAt >= :from and p.awardedAt < :to
            group by p.userId
            order by sum(p.points) desc, count(p) desc
            """)
    List<UserPointsAggregate> topUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                       Pageable pageable);

    @Query("""
            select new com.theclub.eventservice.repository.ClubPointsAggregate(p.clubId, sum(p.points), count(p))
            from PointsLedgerEntry p where p.awardedAt >= :from and p.awardedAt < :to
            group by p.clubId
            order by sum(p.points) desc, count(p) desc
            """)
    List<ClubPointsAggregate> topClubs(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                       Pageable pageable);
}

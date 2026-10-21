package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.Club;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClubRepository extends JpaRepository<Club, UUID> {

    boolean existsByNameIgnoreCase(String name);

    List<Club> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @Query("""
            select c from Club c
            where (:q is null or lower(c.name) like lower(concat('%', :q, '%')))
              and (:city is null or lower(c.city) = lower(:city))
            order by c.name""")
    Page<Club> search(@Param("q") String q, @Param("city") String city, Pageable pageable);

    @Query("select c from Club c where c.id in (select m.clubId from ClubMembership m where m.userId = :userId) order by c.name")
    List<Club> findJoinedBy(@Param("userId") UUID userId);

    /** Clubs with a map position inside the box; callers compute the exact distance. */
    @Query("""
            select c from Club c
            where c.latitude between :minLat and :maxLat and c.longitude between :minLng and :maxLng
            """)
    List<Club> findInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                         @Param("minLng") double minLng, @Param("maxLng") double maxLng);

    /** Most subscribers plus followers first. */
    @Query("""
            select c from Club c
            order by ((select count(m) from ClubMembership m where m.clubId = c.id)
                    + (select count(f) from ClubFollow f where f.clubId = c.id)) desc, c.name
            """)
    List<Club> findPopular(Pageable pageable);
}

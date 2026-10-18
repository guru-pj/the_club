package com.theclub.activityservice.repository;

import com.theclub.activityservice.model.StravaConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StravaConnectionRepository extends JpaRepository<StravaConnection, UUID> {

    Optional<StravaConnection> findByUserId(UUID userId);

    Optional<StravaConnection> findByAthleteId(long athleteId);
}

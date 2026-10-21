package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ClubChatMute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubChatMuteRepository extends JpaRepository<ClubChatMute, UUID> {

    Optional<ClubChatMute> findByClubIdAndUserId(UUID clubId, UUID userId);

    List<ClubChatMute> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    List<ClubChatMute> findByUserId(UUID userId);
}

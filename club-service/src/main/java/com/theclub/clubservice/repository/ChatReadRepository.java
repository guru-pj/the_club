package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ChatRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatReadRepository extends JpaRepository<ChatRead, UUID> {

    Optional<ChatRead> findByConversationIdAndUserId(UUID conversationId, UUID userId);
}

package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByClubId(UUID clubId);

    List<ChatConversation> findByClubIdIn(Collection<UUID> clubIds);

    Optional<ChatConversation> findByUserLowAndUserHigh(UUID userLow, UUID userHigh);

    @Query("select c from ChatConversation c where c.kind = com.theclub.clubservice.model.ConversationKind.DIRECT "
            + "and (c.userLow = :userId or c.userHigh = :userId)")
    List<ChatConversation> findDirectOf(@Param("userId") UUID userId);
}

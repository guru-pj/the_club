package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Newest first; {@code before} pages back through older messages. Messages from {@code hidden} senders are left out. */
    @Query("select m from ChatMessage m where m.conversationId = :conversationId "
            + "and (:before is null or m.id < :before) and m.senderId not in :hidden order by m.id desc")
    List<ChatMessage> page(@Param("conversationId") UUID conversationId, @Param("before") UUID before,
                           @Param("hidden") Collection<UUID> hidden, Pageable pageable);

    @Query("select m from ChatMessage m where m.conversationId = :conversationId and m.deletedAt is null "
            + "and m.senderId not in :hidden order by m.id desc")
    List<ChatMessage> latest(@Param("conversationId") UUID conversationId, @Param("hidden") Collection<UUID> hidden,
                             Pageable pageable);

    /** Conversation ID → messages from others since the person last read it. */
    @Query("select m.conversationId, count(m) from ChatMessage m left join ChatRead r "
            + "on r.conversationId = m.conversationId and r.userId = :userId "
            + "where m.conversationId in :conversationIds and m.senderId <> :userId and m.deletedAt is null "
            + "and m.senderId not in :hidden and (r.lastReadAt is null or m.createdAt > r.lastReadAt) "
            + "group by m.conversationId")
    List<Object[]> unreadCounts(@Param("userId") UUID userId, @Param("conversationIds") Collection<UUID> conversationIds,
                                @Param("hidden") Collection<UUID> hidden);

    /** What changed recently, for passing messages written on other replicas to this one's live connections. */
    @Query("select m from ChatMessage m where m.createdAt >= :since or m.deletedAt >= :since")
    List<ChatMessage> changedSince(@Param("since") LocalDateTime since, Pageable pageable);
}

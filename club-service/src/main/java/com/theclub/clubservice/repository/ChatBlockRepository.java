package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ChatBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatBlockRepository extends JpaRepository<ChatBlock, UUID> {

    Optional<ChatBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<ChatBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    @Query("select b.blockedId from ChatBlock b where b.blockerId = :userId")
    List<UUID> blockedBy(@Param("userId") UUID userId);

    @Query("select b.blockerId from ChatBlock b where b.blockedId = :userId")
    List<UUID> blockersOf(@Param("userId") UUID userId);

    /** Whether either of the two blocked the other. */
    @Query("select count(b) > 0 from ChatBlock b where (b.blockerId = :a and b.blockedId = :b) "
            + "or (b.blockerId = :b and b.blockedId = :a)")
    boolean blockedEitherWay(@Param("a") UUID a, @Param("b") UUID b);
}

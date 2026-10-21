package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ChatReport;
import com.theclub.clubservice.model.ChatReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatReportRepository extends JpaRepository<ChatReport, UUID> {

    boolean existsByMessageIdAndReporterId(UUID messageId, UUID reporterId);

    Page<ChatReport> findByStatusOrderByCreatedAtAsc(ChatReportStatus status, Pageable pageable);

    List<ChatReport> findByMessageIdAndStatus(UUID messageId, ChatReportStatus status);
}

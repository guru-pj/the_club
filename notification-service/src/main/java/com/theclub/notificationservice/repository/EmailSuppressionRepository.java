package com.theclub.notificationservice.repository;

import com.theclub.notificationservice.model.EmailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, UUID> {

    boolean existsByEmail(String email);
}

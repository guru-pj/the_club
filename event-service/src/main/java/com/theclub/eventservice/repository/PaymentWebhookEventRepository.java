package com.theclub.eventservice.repository;

import com.theclub.eventservice.model.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {
}

package com.theclub.clubservice.service;

import com.theclub.clubservice.dto.ChatDtos;

import java.util.Set;

/** Gets a stored message (new or deleted) to whoever is connected, on this replica or all of them. */
public interface ChatFanout {

    void publish(Set<java.util.UUID> recipients, ChatDtos.Message message);
}

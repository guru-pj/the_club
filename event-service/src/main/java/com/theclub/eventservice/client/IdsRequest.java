package com.theclub.eventservice.client;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record IdsRequest(List<UUID> ids) {

    public static IdsRequest of(Collection<UUID> ids) {
        return new IdsRequest(List.copyOf(ids));
    }
}

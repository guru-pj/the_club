package com.theclub.notificationservice.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.theclub.notificationservice.model.EmailSuppression;
import com.theclub.notificationservice.repository.EmailSuppressionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Records hard bounces reported by ZeptoMail's webhook. The payload is parsed defensively: event names are
 * matched case-insensitively and bounced addresses are collected from {@code bounced_recipient} fields, falling
 * back to the original {@code email_info.to} recipients.
 */
@Service
public class BounceService {

    private static final Logger log = LoggerFactory.getLogger(BounceService.class);

    private final EmailSuppressionRepository suppressions;
    private final Clock clock;

    public BounceService(EmailSuppressionRepository suppressions, Clock clock) {
        this.suppressions = suppressions;
        this.clock = clock;
    }

    /** @return the addresses newly suppressed. */
    @Transactional
    public Set<String> handle(JsonNode payload) {
        if (!isHardBounce(payload)) {
            return Set.of();
        }
        Set<String> addresses = new LinkedHashSet<>();
        collect(payload, "bounced_recipient", addresses);
        if (addresses.isEmpty()) {
            payload.findValues("email_info").forEach(info -> info.path("to").forEach(
                    to -> addIfAddress(to.path("email_address").path("address").asText(""), addresses)));
        }
        Set<String> added = new LinkedHashSet<>();
        for (String address : addresses) {
            if (!suppressions.existsByEmail(address)) {
                suppressions.save(new EmailSuppression(address, "Hard bounce reported by ZeptoMail", LocalDateTime.now(clock)));
                added.add(address);
            }
        }
        if (!added.isEmpty()) {
            log.warn("Suppressed {} address(es) after hard bounces", added.size());
        }
        return added;
    }

    static boolean isHardBounce(JsonNode payload) {
        StringBuilder names = new StringBuilder();
        for (JsonNode name : payload.findValues("event_name")) {
            if (name.isArray()) {
                name.forEach(n -> names.append(n.asText()).append(' '));
            } else {
                names.append(name.asText()).append(' ');
            }
        }
        String all = names.toString().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "|");
        return all.contains("hardbounce");
    }

    private static void collect(JsonNode node, String field, Set<String> into) {
        node.findValues(field).forEach(value -> {
            if (value.isArray()) {
                value.forEach(v -> addIfAddress(v.asText(""), into));
            } else {
                addIfAddress(value.asText(""), into);
            }
        });
    }

    private static void addIfAddress(String value, Set<String> into) {
        String address = value.strip().toLowerCase(Locale.ROOT);
        if (address.contains("@") && address.length() <= 254) {
            into.add(address);
        }
    }
}

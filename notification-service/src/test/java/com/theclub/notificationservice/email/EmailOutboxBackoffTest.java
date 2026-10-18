package com.theclub.notificationservice.email;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxBackoffTest {

    @Test
    void backoffGrowsAndThenStaysAtSixHours() {
        assertThat(EmailOutboxService.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(EmailOutboxService.backoff(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(EmailOutboxService.backoff(3)).isEqualTo(Duration.ofMinutes(15));
        assertThat(EmailOutboxService.backoff(4)).isEqualTo(Duration.ofHours(1));
        assertThat(EmailOutboxService.backoff(5)).isEqualTo(Duration.ofHours(6));
        assertThat(EmailOutboxService.backoff(7)).isEqualTo(Duration.ofHours(6));
    }
}

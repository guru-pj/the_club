package com.theclub.common.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidGeneratorTest {

    @Test
    void generatesVersion7Uuids() {
        assertThat(UuidGenerator.generate().version()).isEqualTo(7);
    }

    @Test
    void generatesUniqueIds() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(UuidGenerator.generate());
        }
        assertThat(ids).hasSize(100_000);
    }

    @Test
    void idsAreMonotonicallyOrdered() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidGenerator.generate().toString());
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(String::compareTo);
        assertThat(ids).isEqualTo(sorted);
    }
}

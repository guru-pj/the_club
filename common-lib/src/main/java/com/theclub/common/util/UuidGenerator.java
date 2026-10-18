package com.theclub.common.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/**
 * Generates time-ordered UUIDv7 identifiers inside the service, so IDs are known
 * before persisting and stay index-friendly in MySQL and PostgreSQL.
 */
public final class UuidGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidGenerator() {
    }

    public static UUID generate() {
        return GENERATOR.generate();
    }
}

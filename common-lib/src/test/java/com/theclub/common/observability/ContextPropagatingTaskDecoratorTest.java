package com.theclub.common.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPropagatingTaskDecoratorTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void copiesTheCallersMdcIntoTheTaskAndRestoresTheWorkerThread() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> MDC.put("stale", "x")).get();
            MDC.put(RequestIds.MDC_KEY, "req-7");
            AtomicReference<String> seen = new AtomicReference<>();
            AtomicReference<String> stale = new AtomicReference<>();

            Runnable task = new ContextPropagatingTaskDecorator().decorate(() -> {
                seen.set(MDC.get(RequestIds.MDC_KEY));
                stale.set(MDC.get("stale"));
            });
            CompletableFuture.runAsync(task, executor).get();

            assertThat(seen.get()).isEqualTo("req-7");
            assertThat(stale.get()).isNull();
            assertThat(executor.submit(() -> MDC.get("stale")).get()).isEqualTo("x");
        } finally {
            executor.shutdownNow();
        }
    }
}

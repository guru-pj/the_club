package com.theclub.common.observability;

import io.micrometer.context.ContextSnapshotFactory;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.ClassUtils;

import java.util.Map;

/**
 * Carries the logging MDC (request id) and, when Micrometer context propagation is present, the current
 * trace/observation into @Async tasks, so background work (e.g. sending an email) logs and traces under the
 * request that triggered it.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    private static final boolean CONTEXT_PROPAGATION_PRESENT =
            ClassUtils.isPresent("io.micrometer.context.ContextSnapshotFactory", null);

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Runnable task = CONTEXT_PROPAGATION_PRESENT
                ? ContextSnapshotFactory.builder().build().captureAll().wrap(runnable)
                : runnable;
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (mdc != null) {
                MDC.setContextMap(mdc);
            } else {
                MDC.clear();
            }
            try {
                task.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}

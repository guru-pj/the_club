package com.theclub.common.config;

import com.theclub.common.security.SecurityProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to start with development placeholder secrets when the {@code prod} profile is active.
 */
public class ProductionSafetyCheck implements InitializingBean {

    static final int MIN_INTERNAL_KEY_LENGTH = 32;
    private static final String PLACEHOLDER = "change-me";

    private final Environment environment;
    private final SecurityProperties security;

    public ProductionSafetyCheck(Environment environment, SecurityProperties security) {
        this.environment = environment;
        this.security = security;
    }

    @Override
    public void afterPropertiesSet() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> problems = new ArrayList<>();
        String internalKey = security.internalApiKey();
        if (internalKey == null || internalKey.length() < MIN_INTERNAL_KEY_LENGTH) {
            problems.add("theclub.security.internal-api-key (INTERNAL_API_KEY) must be at least "
                    + MIN_INTERNAL_KEY_LENGTH + " characters");
        } else if (isPlaceholder(internalKey)) {
            problems.add("theclub.security.internal-api-key (INTERNAL_API_KEY) still has its placeholder value");
        }
        if (isPlaceholder(environment.getProperty("theclub.admin.password"))) {
            problems.add("theclub.admin.password (ADMIN_PASSWORD) still has its placeholder value");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the 'prod' profile: " + String.join("; ", problems));
        }
    }

    private static boolean isPlaceholder(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(PLACEHOLDER);
    }
}

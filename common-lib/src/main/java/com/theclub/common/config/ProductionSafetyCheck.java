package com.theclub.common.config;

import com.theclub.common.security.SecurityProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Refuses to start with development settings in production.
 * <p>
 * With the {@code prod} profile: no placeholder secrets, and none of the development switches that weaken
 * accounts (non-Secure refresh cookie, unverified emails allowed, a placeholder admin mailbox, email links to
 * plain HTTP or localhost). Each setting is checked only in the services that read it.
 * <p>
 * Without the {@code prod} profile: an {@code APP_BASE_URL} on a public HTTPS host means a real deployment
 * running the development profile (mock payments, none of the checks above), so that is refused as well unless
 * {@code ALLOW_DEV_PROFILE_ON_PUBLIC_URL=true} says it is deliberate (for example an HTTPS tunnel to a laptop).
 */
public class ProductionSafetyCheck implements InitializingBean {

    static final int MIN_INTERNAL_KEY_LENGTH = 32;
    private static final String PLACEHOLDER = "change-me";
    static final String BASE_URL = "theclub.app.base-url";
    static final String ALLOW_DEV_ON_PUBLIC_URL = "theclub.allow-dev-profile-on-public-url";
    /** Mailboxes that can't receive mail: the template's admin@theclub.local, example domains, localhost. */
    private static final Pattern PLACEHOLDER_EMAIL =
            Pattern.compile("(?i).*@(?:[^@]*\\.)?(?:local|localhost|example\\.(?:com|org|net)|invalid|test)$");
    /** The documentation's example domains, e.g. values-prod.yaml's appBaseUrl https://theclub.example.com. */
    private static final Pattern PLACEHOLDER_HOST = Pattern.compile("(?i)(?:.*\\.)?example\\.(?:com|org|net)");
    /** Hosts that only exist on a developer machine or a private network. */
    private static final Pattern LOCAL_HOST = Pattern.compile(
            "(?i)localhost|.*\\.localhost|.*\\.local|127(?:\\.\\d+){3}|\\[?::1]?|10(?:\\.\\d+){3}|192\\.168(?:\\.\\d+){2}"
                    + "|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d+){2}");

    private final Environment environment;
    private final SecurityProperties security;

    public ProductionSafetyCheck(Environment environment, SecurityProperties security) {
        this.environment = environment;
        this.security = security;
    }

    @Override
    public void afterPropertiesSet() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            refuseDevProfileOnPublicUrl();
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
        if ("false".equalsIgnoreCase(environment.getProperty("theclub.auth.cookie-secure"))) {
            problems.add("theclub.auth.cookie-secure (AUTH_COOKIE_SECURE) is false: the refresh cookie would be sent over "
                    + "plain HTTP");
        }
        if ("false".equalsIgnoreCase(environment.getProperty("theclub.auth.require-verified-email"))) {
            problems.add("theclub.auth.require-verified-email (REQUIRE_EMAIL_VERIFICATION) is false: accounts could log in "
                    + "with an email address they don't own");
        }
        String adminEmail = environment.getProperty("theclub.admin.email");
        if (adminEmail != null && PLACEHOLDER_EMAIL.matcher(adminEmail.trim()).matches()) {
            problems.add("theclub.admin.email (ADMIN_EMAIL) is '" + adminEmail.trim() + "', which can't receive mail "
                    + "(password resets for the admin would be lost)");
        }
        String baseUrl = environment.getProperty(BASE_URL);
        if (baseUrl != null && !isPublicHttps(baseUrl)) {
            problems.add("theclub.app.base-url (APP_BASE_URL) is '" + baseUrl + "': links in emails must point at the "
                    + "public https:// address of the web app");
        } else if (baseUrl != null && PLACEHOLDER_HOST.matcher(URI.create(baseUrl.trim()).getHost()).matches()) {
            problems.add("theclub.app.base-url (APP_BASE_URL) is still the example address " + baseUrl);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the 'prod' profile: " + String.join("; ", problems));
        }
    }

    private void refuseDevProfileOnPublicUrl() {
        String baseUrl = environment.getProperty(BASE_URL);
        if (baseUrl == null || !isPublicHttps(baseUrl)
                || "true".equalsIgnoreCase(environment.getProperty(ALLOW_DEV_ON_PUBLIC_URL))) {
            return;
        }
        throw new IllegalStateException("Refusing to start: APP_BASE_URL is the public address " + baseUrl
                + " but the 'prod' profile is not active, so development settings (mock payments, no safety checks) "
                + "would be live. Set SPRING_PROFILES_ACTIVE=prod, or ALLOW_DEV_PROFILE_ON_PUBLIC_URL=true if this is "
                + "a deliberate test setup.");
    }

    static boolean isPublicHttps(String url) {
        try {
            URI uri = URI.create(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && !LOCAL_HOST.matcher(uri.getHost()).matches();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isPlaceholder(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(PLACEHOLDER);
    }
}

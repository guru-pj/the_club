package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-encrypts tokens stored in plaintext before at-rest encryption was introduced. Idempotent: rows that are
 * already encrypted are skipped, so every replica can run it on startup.
 */
@Component
public class StravaTokenMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StravaTokenMigration.class);

    private final StravaConnectionRepository connections;
    private final StravaTokenCipher cipher;

    public StravaTokenMigration(StravaConnectionRepository connections, StravaTokenCipher cipher) {
        this.connections = connections;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!cipher.isEnabled()) {
            return;
        }
        int migrated = 0;
        for (StravaConnection connection : connections.findAll()) {
            boolean changed = false;
            if (connection.getAccessToken() != null && !StravaTokenCipher.isEncrypted(connection.getAccessToken())) {
                connection.setAccessToken(cipher.encrypt(connection.getAccessToken()));
                changed = true;
            }
            if (connection.getRefreshToken() != null && !StravaTokenCipher.isEncrypted(connection.getRefreshToken())) {
                connection.setRefreshToken(cipher.encrypt(connection.getRefreshToken()));
                changed = true;
            }
            if (changed) {
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("Encrypted Strava tokens of {} existing connection(s)", migrated);
        }
    }
}

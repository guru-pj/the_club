package com.theclub.activityservice.strava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts Strava OAuth tokens at rest with AES-256-GCM.
 * <p>
 * Stored format: {@code v1:} + base64(12-byte IV || ciphertext || 16-byte tag). Values without the prefix are
 * legacy plaintext (written before encryption existed) and are returned as-is by {@link #decrypt}; they are
 * re-encrypted on startup by {@link StravaTokenMigration}. Without a configured key (local development only;
 * refused under the prod profile) tokens are stored in plaintext.
 */
@Component
public class StravaTokenCipher {

    static final String PREFIX = "v1:";
    private static final Logger log = LoggerFactory.getLogger(StravaTokenCipher.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public StravaTokenCipher(StravaProperties properties) {
        this(properties.tokenEncryptionKey());
    }

    StravaTokenCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            log.warn("STRAVA_TOKEN_ENCRYPTION_KEY is not set: Strava tokens are stored unencrypted (development only)");
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("STRAVA_TOKEN_ENCRYPTION_KEY must be base64", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("STRAVA_TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public boolean isEnabled() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || key == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_BYTES + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt Strava token", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        if (key == null) {
            throw new IllegalStateException("A Strava token is encrypted but STRAVA_TOKEN_ENCRYPTION_KEY is not set");
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a Strava token (wrong STRAVA_TOKEN_ENCRYPTION_KEY?)", e);
        }
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}

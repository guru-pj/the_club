package com.theclub.activityservice.strava;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StravaTokenCipherTest {

    static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    static final String OTHER_KEY = Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes());

    @Test
    void roundTripsWithAFreshIvEachTime() {
        StravaTokenCipher cipher = new StravaTokenCipher(KEY);
        String first = cipher.encrypt("a1b2c3d4e5f6");
        String second = cipher.encrypt("a1b2c3d4e5f6");

        assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("a1b2c3d4e5f6");
        assertThat(cipher.decrypt(first)).isEqualTo("a1b2c3d4e5f6");
        assertThat(cipher.decrypt(second)).isEqualTo("a1b2c3d4e5f6");
    }

    @Test
    void legacyPlaintextPassesThroughUntilMigrated() {
        assertThat(new StravaTokenCipher(KEY).decrypt("legacy-plain-token")).isEqualTo("legacy-plain-token");
        assertThat(StravaTokenCipher.isEncrypted("legacy-plain-token")).isFalse();
    }

    @Test
    void tamperedOrWrongKeyCiphertextIsRejected() {
        String sealed = new StravaTokenCipher(KEY).encrypt("secret");
        assertThatThrownBy(() -> new StravaTokenCipher(OTHER_KEY).decrypt(sealed))
                .isInstanceOf(IllegalStateException.class);
        String tampered = sealed.substring(0, sealed.length() - 4) + "AAAA";
        assertThatThrownBy(() -> new StravaTokenCipher(KEY).decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyMustBe32Bytes() {
        assertThatThrownBy(() -> new StravaTokenCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new StravaTokenCipher("not base64 !!")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutKeyTokensStayPlainButEncryptedOnesCannotBeRead() {
        StravaTokenCipher none = new StravaTokenCipher((String) null);
        assertThat(none.isEnabled()).isFalse();
        assertThat(none.encrypt("plain")).isEqualTo("plain");
        String sealed = new StravaTokenCipher(KEY).encrypt("secret");
        assertThatThrownBy(() -> none.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }
}

package com.theclub.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaKeysTest {

    private static String pem(String resource) throws IOException {
        try (InputStream in = RsaKeysTest.class.getResourceAsStream("/" + resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void loadsInlinePemIncludingEscapedNewlines() throws IOException {
        String pem = pem("jwt-test-public.pem");

        assertThat(RsaKeys.publicKey(pem, "p").getModulus().bitLength()).isEqualTo(2048);
        assertThat(RsaKeys.publicKey(pem.replace("\n", "\\n"), "p").getModulus().bitLength()).isEqualTo(2048);
    }

    @Test
    void loadsFromFileLocation(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("private.pem");
        Files.writeString(file, pem("jwt-test-private.pem"));

        assertThat(RsaKeys.privateKey("file:" + file, "p").getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void reportsWhichPropertyIsBroken() {
        assertThatThrownBy(() -> RsaKeys.privateKey("classpath:missing.pem", "theclub.jwt.private-key"))
                .hasMessageContaining("theclub.jwt.private-key");
        assertThatThrownBy(() -> RsaKeys.publicKey("-----BEGIN RSA PUBLIC KEY-----\nAAAA\n-----END RSA PUBLIC KEY-----", "x"))
                .hasMessageContaining("PKCS#1");
        assertThatThrownBy(() -> RsaKeys.publicKey("", "x")).hasMessageContaining("not configured");
    }
}

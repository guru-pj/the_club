package com.theclub.common.security;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads RSA keys from PEM text or from a {@code file:}/{@code classpath:} location.
 */
public final class RsaKeys {

    private RsaKeys() {
    }

    public static RSAPublicKey publicKey(String value, String property) {
        byte[] der = decode(value, "PUBLIC KEY", property);
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException(property + " is not a valid RSA public key (expected X.509 PEM)", e);
        }
    }

    public static RSAPrivateKey privateKey(String value, String property) {
        byte[] der = decode(value, "PRIVATE KEY", property);
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException(property + " is not a valid RSA private key (expected PKCS#8 PEM)", e);
        }
    }

    private static byte[] decode(String value, String type, String property) {
        String pem = read(value, property)
                // env vars often carry PEM with literal "\n" sequences
                .replace("\\n", "\n");
        if (pem.contains("-----BEGIN RSA ")) {
            throw new IllegalStateException(property + " uses PKCS#1 (BEGIN RSA ...); convert it with "
                    + "'openssl pkey -in key.pem -out key-pkcs8.pem'");
        }
        String body = pem.replaceAll("-----(BEGIN|END) " + type + "-----", "").replaceAll("\\s", "");
        if (body.isEmpty() || body.contains("-----")) {
            throw new IllegalStateException(property + " must be a PEM '" + type + "'");
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(property + " contains invalid base64", e);
        }
    }

    private static String read(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is not configured");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("file:") || trimmed.startsWith("classpath:")) {
            Resource resource = new DefaultResourceLoader().getResource(trimmed);
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(property + " could not be read from " + trimmed, e);
            }
        }
        return trimmed;
    }
}

package com.theclub.eventservice.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * HMAC-SHA256 signatures as used by Razorpay: "orderId|paymentId" for checkout, the raw body for webhooks.
 */
final class HmacSigner {

    private HmacSigner() {
    }

    static String sign(String secret, String orderId, String paymentId) {
        return hex(secret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
    }

    static boolean matches(String secret, String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null) {
            return false;
        }
        return constantTimeEquals(sign(secret, orderId, paymentId), signature);
    }

    /** Signature over the exact bytes received, never a re-serialized copy. */
    static String signBody(String secret, byte[] body) {
        return hex(secret, body);
    }

    static boolean bodyMatches(String secret, byte[] body, String signature) {
        if (secret == null || secret.isBlank() || body == null) {
            return false;
        }
        return constantTimeEquals(signBody(secret, body), signature);
    }

    private static boolean constantTimeEquals(String expected, String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}

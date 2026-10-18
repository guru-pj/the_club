package com.theclub.authservice.controller;

import com.theclub.common.security.JwtService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the token verification keys (RFC 7517) so other systems can verify The Club access tokens.
 */
@RestController
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/api/v1/auth/.well-known/jwks.json")
    public ResponseEntity<Map<String, List<Map<String, String>>>> jwks() {
        List<Map<String, String>> keys = new ArrayList<>();
        jwtService.verificationKeys().forEach((kid, key) -> keys.add(toJwk(kid, key)));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(Map.of("keys", keys));
    }

    private static Map<String, String> toJwk(String kid, RSAPublicKey key) {
        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", kid);
        jwk.put("n", base64Url(key.getModulus()));
        jwk.put("e", base64Url(key.getPublicExponent()));
        return jwk;
    }

    /** Unsigned big-endian bytes, as JWK requires (drop BigInteger's sign byte). */
    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

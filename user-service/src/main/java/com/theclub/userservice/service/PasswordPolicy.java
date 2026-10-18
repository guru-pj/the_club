package com.theclub.userservice.service;

import com.theclub.common.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Password rules for registration, reset and change: 8–72 characters (BCrypt uses the first 72 bytes),
 * not the account's email, and not one of the most common passwords.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    static final String TOO_COMMON = "This password is too common";

    private static final Set<String> COMMON = Set.of(
            "12345678", "123456789", "1234567890", "12345678910", "123123123", "11111111", "00000000", "88888888",
            "87654321", "11223344", "12341234", "1q2w3e4r", "1qaz2wsx", "qwertyui", "qwertyuiop", "qwerty123",
            "qwerty12", "qwerty1234", "asdfghjk", "asdfasdf", "zxcvbnm1", "password", "password1", "password12",
            "password123", "password1234", "passw0rd", "p@ssw0rd", "p@ssword", "pa$$word", "password!", "iloveyou",
            "iloveyou1", "princess", "princess1", "sunshine", "sunshine1", "football", "football1", "baseball",
            "basketball", "superman", "batman123", "starwars", "trustno1", "welcome1", "welcome123", "letmein1",
            "letmein123", "admin123", "admin1234", "administrator", "changeme", "changeme1", "default1", "master123",
            "monkey123", "dragon123", "shadow123", "michael1", "jennifer", "computer", "internet", "whatever",
            "abcd1234", "abc12345", "abcdefgh", "a1b2c3d4", "aa123456", "qazwsxedc", "q1w2e3r4", "q1w2e3r4t5",
            "zaq12wsx", "liverpool", "chelsea1", "arsenal1", "cricket1", "mustang1", "charlie1", "jordan23",
            "michelle", "jessica1", "ashley12", "hello123", "hellohello", "secret123", "test1234", "testtest",
            "guest123", "user1234", "login123", "loveme123", "freedom1", "running1", "runner123", "theclub1",
            "theclub123", "fitness1", "india123", "chennai1");

    /**
     * @param field JSON field name the error is reported against.
     * @throws ApiException 400 with a field error when the password breaks a rule.
     */
    public void check(String password, String email, String field) {
        String message = violation(password, email);
        if (message != null) {
            throw ApiException.badRequest("Validation failed").withFieldError(field, message);
        }
    }

    static String violation(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters";
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (email != null && normalized.equals(email.trim().toLowerCase(Locale.ROOT))) {
            return "must not be the same as your email address";
        }
        if (COMMON.contains(normalized)) {
            return TOO_COMMON;
        }
        return null;
    }
}

package com.theclub.userservice.service;

import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsReasonablePasswords() {
        assertThatCode(() -> policy.check("Str0ng-pass!", "a@b.com", "password")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("correct horse battery staple", "a@b.com", "password")).doesNotThrowAnyException();
    }

    @Test
    void rejectsCommonPasswordsCaseInsensitively() {
        assertThat(PasswordPolicy.violation("password123", "a@b.com")).isEqualTo(PasswordPolicy.TOO_COMMON);
        assertThat(PasswordPolicy.violation("PassWord123", "a@b.com")).isEqualTo(PasswordPolicy.TOO_COMMON);
        assertThat(PasswordPolicy.violation("12345678", "a@b.com")).isEqualTo(PasswordPolicy.TOO_COMMON);
    }

    @Test
    void rejectsLengthAndEmailReuse() {
        assertThat(PasswordPolicy.violation("short", "a@b.com")).contains("between 8 and 72");
        assertThat(PasswordPolicy.violation("x".repeat(73), "a@b.com")).contains("between 8 and 72");
        assertThat(PasswordPolicy.violation("Asha@Example.com", "asha@example.com")).contains("email");
    }

    @Test
    void reportsTheViolationOnTheGivenField() {
        assertThatThrownBy(() -> policy.check("qwerty123", "a@b.com", "newPassword"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getFieldErrors()).containsEntry("newPassword", PasswordPolicy.TOO_COMMON));
    }
}

package com.example.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

/** SignupRequest의 Bean Validation 메시지가 API_SPEC.md 2.1 표와 정확히 일치하는지 검증한다. */
class SignupRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void invalidEmailFormatTriggersEmailMessage() {
        SignupRequest request = new SignupRequest("invalid-email", "abcdef", "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("올바른 이메일 형식이 아닙니다", violations.iterator().next().getMessage());
    }

    @Test
    void passwordShorterThanSixCharsTriggersPasswordMessage() {
        SignupRequest request = new SignupRequest("user@example.com", "abc12", "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("비밀번호는 6자 이상이어야 합니다", violations.iterator().next().getMessage());
    }

    @Test
    void blankNicknameTriggersNicknameMessage() {
        SignupRequest request = new SignupRequest("user@example.com", "abcdef", "");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("닉네임을 입력해 주세요", violations.iterator().next().getMessage());
    }

    @Test
    void validRequestHasNoViolations() {
        SignupRequest request = new SignupRequest("user@example.com", "abcdef", "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertEquals(0, violations.size());
    }
}

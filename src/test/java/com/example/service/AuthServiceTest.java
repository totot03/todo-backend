package com.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.dto.auth.LoginRequest;
import com.example.dto.auth.SignupRequest;
import com.example.entity.Provider;
import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.security.JwtTokenProvider;

/** AuthService의 회원가입/로그인 비즈니스 로직을 검증한다. 특히 로그인 실패 3가지 원인이 모두 동일한 LOGIN_FAILED로 흡수되는지 확인한다. */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final AuthService authService =
            new AuthService(userRepository, passwordEncoder, jwtTokenProvider);

    @Test
    void signupThrowsEmailDuplicatedWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);
        SignupRequest request = new SignupRequest("user@example.com", "abcdef", "홍길동");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.signup(request));

        assertEquals(ErrorCode.EMAIL_DUPLICATED, exception.getErrorCode());
    }

    @Test
    void signupSavesUserWithBcryptHashedPasswordNotPlainText() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.createToken(any())).thenReturn("fake-token");
        SignupRequest request = new SignupRequest("user@example.com", "abcdef", "홍길동");

        AuthService.AuthResult result = authService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertNotEquals("abcdef", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("abcdef", savedUser.getPassword()));
        assertEquals("fake-token", result.token());
    }

    @Test
    void loginThrowsLoginFailedWhenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest("missing@example.com", "abcdef");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.login(request));

        assertEquals(ErrorCode.LOGIN_FAILED, exception.getErrorCode());
    }

    @Test
    void loginThrowsLoginFailedWhenPasswordDoesNotMatch() {
        User user =
                User.builder()
                        .id(1L)
                        .email("user@example.com")
                        .password(passwordEncoder.encode("correct-password"))
                        .provider(Provider.LOCAL)
                        .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.login(request));

        assertEquals(ErrorCode.LOGIN_FAILED, exception.getErrorCode());
    }

    @Test
    void loginThrowsLoginFailedForSocialOnlyAccountWithNullPassword() {
        User user =
                User.builder()
                        .id(1L)
                        .email("social@example.com")
                        .password(null)
                        .provider(Provider.GOOGLE)
                        .build();
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.of(user));
        LoginRequest request = new LoginRequest("social@example.com", "anything");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.login(request));

        assertEquals(ErrorCode.LOGIN_FAILED, exception.getErrorCode());
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        User user =
                User.builder()
                        .id(1L)
                        .email("user@example.com")
                        .password(passwordEncoder.encode("abcdef"))
                        .provider(Provider.LOCAL)
                        .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createToken(1L)).thenReturn("fake-token");
        LoginRequest request = new LoginRequest("user@example.com", "abcdef");

        AuthService.AuthResult result = authService.login(request);

        assertEquals("fake-token", result.token());
        assertEquals("user@example.com", result.user().email());
    }
}

package com.example.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.entity.Provider;
import com.example.entity.User;
import com.example.repository.UserRepository;

/**
 * 실제 HTTP 호출이 필요한 loadUser() 대신, 네트워크 의존 없이 테스트 가능한 mapToCustomOAuth2User()로 매핑/계정 연결 로직(FR-A09
 * 핵심)을 검증한다.
 */
class CustomOAuth2UserServiceTest {

    private static final String NAME_ATTRIBUTE_KEY = "sub";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomOAuth2UserService customOAuth2UserService =
            new CustomOAuth2UserService(userRepository);

    @Test
    void createsNewUserWithGoogleProviderAndNullPasswordWhenEmailNotFound() {
        Map<String, Object> attributes =
                Map.of("sub", "google-uid-1", "email", "new@example.com", "name", "새사용자");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomOAuth2User result =
                customOAuth2UserService.mapToCustomOAuth2User(attributes, NAME_ATTRIBUTE_KEY);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertEquals("new@example.com", savedUser.getEmail());
        assertEquals("새사용자", savedUser.getNickname());
        assertEquals(Provider.GOOGLE, savedUser.getProvider());
        assertNull(savedUser.getPassword());
        assertEquals("google-uid-1", result.getName());
    }

    @Test
    void reusesExistingLocalAccountWithoutCreatingDuplicateWhenEmailAlreadyExists() {
        User existingLocalUser =
                User.builder()
                        .id(1L)
                        .email("existing@example.com")
                        .password("bcrypt-hash")
                        .nickname("기존사용자")
                        .provider(Provider.LOCAL)
                        .build();
        Map<String, Object> attributes =
                Map.of("sub", "google-uid-2", "email", "existing@example.com", "name", "구글이름");
        when(userRepository.findByEmail("existing@example.com"))
                .thenReturn(Optional.of(existingLocalUser));

        CustomOAuth2User result =
                customOAuth2UserService.mapToCustomOAuth2User(attributes, NAME_ATTRIBUTE_KEY);

        verify(userRepository, never()).save(any(User.class));
        assertEquals(Provider.LOCAL, existingLocalUser.getProvider());
        assertEquals(1L, result.getUserId());
    }

    @Test
    void truncatesNicknameLongerThan50CharsToAvoidColumnLengthViolation() {
        String longName = "가".repeat(60);
        Map<String, Object> attributes =
                Map.of("sub", "google-uid-3", "email", "longname@example.com", "name", longName);
        when(userRepository.findByEmail("longname@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        customOAuth2UserService.mapToCustomOAuth2User(attributes, NAME_ATTRIBUTE_KEY);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(50, captor.getValue().getNickname().length());
    }
}

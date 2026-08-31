package com.example.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.entity.Provider;
import com.example.entity.User;
import com.example.repository.UserRepository;

/** JwtAuthenticationFilter가 쿠키 유무·토큰 유효성에 따라 SecurityContext를 올바르게 채우거나 비워두는지 검증한다. */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-jwt-must-be-long-enough-32bytes!!";
    private static final String COOKIE_NAME = "access_token";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 86_400_000L);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtTokenProvider, userRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenCookieFillsSecurityContext() throws Exception {
        User user =
                User.builder().id(1L).email("user@example.com").provider(Provider.LOCAL).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        String token = jwtTokenProvider.createToken(1L);

        doFilter(cookieRequest(token));

        assertEquals(
                "user@example.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void noCookieLeavesSecurityContextEmpty() throws Exception {
        doFilter(new MockHttpServletRequest());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tamperedTokenLeavesSecurityContextEmpty() throws Exception {
        String token = jwtTokenProvider.createToken(1L);
        String tampered =
                token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        doFilter(cookieRequest(tampered));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void expiredTokenLeavesSecurityContextEmpty() throws Exception {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1000L);
        String expiredToken = expiredProvider.createToken(1L);

        doFilter(cookieRequest(expiredToken));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validTokenButUserNotFoundLeavesSecurityContextEmpty() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        String token = jwtTokenProvider.createToken(999L);

        doFilter(cookieRequest(token));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest cookieRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, token));
        return request;
    }

    private void doFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}

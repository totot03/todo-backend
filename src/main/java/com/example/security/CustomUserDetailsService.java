package com.example.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.repository.UserRepository;

/**
 * email 기준으로 사용자를 조회하는 UserDetailsService. 로그인 시 AuthenticationManager가 이 경로를 거친다.
 *
 * <p>요청마다 실행되는 JWT 인증(JwtAuthenticationFilter)은 이 서비스를 거치지 않고 User.id로 직접 조회한다 — email 기준 조회(로그인)와
 * id 기준 조회(요청 인증)를 혼동하지 않는다.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository
                .findByEmail(email)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }
}

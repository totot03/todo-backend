package com.example.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.entity.User;

/**
 * User 엔티티를 Spring Security 인증 파이프라인에 연결하는 UserDetails 구현체.
 *
 * <p>{@link #getUsername()}은 로그인 식별자인 email을 반환한다 — User.id는 JWT subject 전용이며 이 클래스와는 별개 경로다. 소셜
 * 가입자는 password가 null일 수 있는데, 자격 증명 기반 로그인 자체를 시도하지 않는 경로이므로 그대로 null을 반환해도 안전하다. 권한은 M2-A 범위에서 관리자
 * 기능이 없으므로 ROLE_USER로 고정한다.
 */
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}

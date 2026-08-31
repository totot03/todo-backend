package com.example.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.example.entity.User;

/**
 * 구글 OAuth2 인증이 끝난 사용자를 Spring Security 파이프라인에 연결하는 {@link OAuth2User} 구현체.
 *
 * <p>{@link CustomUserDetails}와 대응되는 소셜 로그인 버전이다 — {@code getUserId()}로 {@link
 * com.example.security.OAuth2SuccessHandler}(다음 태스크)가 JWT를 발급할 수 있게 하고, 권한은 M2-A와 동일하게 관리자 기능이 없으므로
 * ROLE_USER로 고정한다. {@code getName()}이 반환할 속성 키({@code nameAttributeKey})는 구글에 고정된 "sub"를 하드코딩하지 않고
 * {@link CustomOAuth2UserService}가 {@code ClientRegistration}에서 읽어와 넘겨준다 — provider 설정이 바뀌어도 이 클래스를
 * 건드릴 필요가 없도록 하기 위함이다.
 */
public class CustomOAuth2User implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;
    private final String nameAttributeKey;

    public CustomOAuth2User(User user, Map<String, Object> attributes, String nameAttributeKey) {
        this.user = user;
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
    }

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return String.valueOf(attributes.get(nameAttributeKey));
    }
}

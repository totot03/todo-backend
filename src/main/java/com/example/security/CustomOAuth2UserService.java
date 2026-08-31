package com.example.security;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.entity.Provider;
import com.example.entity.Role;
import com.example.entity.User;
import com.example.repository.UserRepository;

/**
 * 구글 OAuth2 인증 성공 후 사용자 정보를 조회해 {@link User} 엔티티와 연결하는 {@link OAuth2UserService}.
 *
 * <p>이메일이 이미 LOCAL 계정으로 가입돼 있으면 {@code provider}를 덮어쓰지 않고 그 계정을 그대로 재사용한다 — {@code User.provider}가
 * 단일 컬럼이라 다중 로그인 수단을 표현할 스키마가 없고, FR-A09(계정 연결)는 "중복 생성 방지"만 요구할 뿐 provider 전환까지 요구하지 않기 때문이다. 처음
 * 보는 이메일이면 {@code provider=GOOGLE, password=null}로 신규 생성한다.
 *
 * <p>{@link #loadUser(OAuth2UserRequest)}는 {@code super.loadUser(...)}에서 구글 UserInfo 엔드포인트로 실제 HTTP
 * 요청을 보내므로, 매핑/계정 연결 로직만 {@link #mapToCustomOAuth2User(Map, String)}로 분리해 네트워크 호출 없이 단위 테스트할 수 있게
 * 했다.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final int NICKNAME_MAX_LENGTH = 50;

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String nameAttributeKey =
                userRequest
                        .getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName();
        return mapToCustomOAuth2User(oAuth2User.getAttributes(), nameAttributeKey);
    }

    /** 구글 프로필(email, name)로 기존 User를 조회하거나 신규 생성한다. */
    CustomOAuth2User mapToCustomOAuth2User(
            Map<String, Object> attributes, String nameAttributeKey) {
        String email = (String) attributes.get("email");
        String nickname = truncateNickname((String) attributes.get("name"));

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseGet(
                                () ->
                                        userRepository.save(
                                                User.builder()
                                                        .email(email)
                                                        .nickname(nickname)
                                                        .provider(Provider.GOOGLE)
                                                        .password(null)
                                                        .role(Role.USER)
                                                        .build()));

        return new CustomOAuth2User(user, attributes, nameAttributeKey);
    }

    /**
     * 닉네임 50자 제한(CLAUDE.md, User.nickname 컬럼 length=50)은 SignupRequest의 Bean Validation이 지켜주지만, 구글
     * 프로필의 name은 그 검증을 거치지 않는 경로라 여기서 직접 잘라준다 — 안 그러면 드물게 이름이 긴 사용자가 DB 컬럼 길이 제약 예외로 로그인 자체가 실패할 수
     * 있다.
     */
    private String truncateNickname(String nickname) {
        if (nickname != null && nickname.length() > NICKNAME_MAX_LENGTH) {
            return nickname.substring(0, NICKNAME_MAX_LENGTH);
        }
        return nickname;
    }
}

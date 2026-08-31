package com.example.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.dto.auth.LoginRequest;
import com.example.dto.auth.SignupRequest;
import com.example.dto.auth.UserResponse;
import com.example.entity.Provider;
import com.example.entity.Role;
import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.security.JwtTokenProvider;

/**
 * 회원가입·로그인 비즈니스 로직.
 *
 * <p>두 메서드 모두 성공 시 {@link AuthResult}(UserResponse + JWT)를 반환한다 — 컨트롤러가 응답 바디와 Set-Cookie를 한 번에 구성할
 * 수 있도록 하기 위함이다. 토큰 값 자체는 이 서비스가 몰라도 되는 컨트롤러 관심사(쿠키 배선)로 넘기지 않고, 여기서 발급까지 마쳐 컨트롤러는 오직 전달만 한다.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** 이메일 중복 체크 후 BCrypt로 비밀번호를 해싱해 저장하고, 가입 즉시 로그인 상태가 되도록 토큰을 함께 발급한다. */
    @Transactional
    public AuthResult signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user =
                User.builder()
                        .email(request.email())
                        .password(passwordEncoder.encode(request.password()))
                        .nickname(request.nickname())
                        .provider(Provider.LOCAL)
                        .role(Role.USER)
                        .build();
        userRepository.save(user);

        return new AuthResult(UserResponse.from(user), jwtTokenProvider.createToken(user.getId()));
    }

    /**
     * 계정 없음·비밀번호 불일치·소셜 전용 계정(password가 null)을 단일 조건식으로 묶어 예외 없이 하나의 LOGIN_FAILED로만 응답한다 — 원인을 구분해
     * 분기하면 계정 존재 여부가 새는 열거 공격(enumeration attack)이 가능해지므로 API_SPEC.md 2.2가 의도적으로 이를 금지한다.
     */
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null
                || user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        return new AuthResult(UserResponse.from(user), jwtTokenProvider.createToken(user.getId()));
    }

    /** 회원가입/로그인 성공 시 컨트롤러가 응답 바디(user)와 Set-Cookie(token)를 구성하는 데 필요한 값을 함께 담는다. */
    public record AuthResult(UserResponse user, String token) {}
}

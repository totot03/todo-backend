package com.example.dto.auth;

import com.example.entity.User;

/**
 * 사용자 정보 응답. API_SPEC.md 2.1~2.4의 공통 응답 데이터.
 *
 * <p>User 엔티티를 컨트롤러 응답에 직접 노출하지 않기 위한 변환 대상이다(비밀번호 해시 등 민감 필드 유출 방지).
 *
 * @param id 사용자 ID
 * @param email 이메일
 * @param nickname 닉네임
 * @param provider 가입 경로(LOCAL/GOOGLE)
 */
public record UserResponse(Long id, String email, String nickname, String provider) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getProvider().name());
    }
}

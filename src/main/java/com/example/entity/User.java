package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLRestriction;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 계정 (PRD 8.2).
 *
 * <p>{@code email}은 활성 계정(deleted_at IS NULL) 내에서만 유일하다. 이 유일성은 일반 UNIQUE 제약이 아니라 {@code
 * schema.sql}의 부분 유니크 인덱스({@code ux_users_email_active})로 적용한다 — 탈퇴 계정의 이메일로 재가입할 수 있어야 하기
 * 때문이다(FR-A12). 그래서 아래 {@code email} 컬럼에는 의도적으로 {@code unique = true}를 두지 않는다.
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /** BCrypt 해시. 소셜 가입자는 NULL. */
    @Column private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;
}

package com.example.entity;

/**
 * 사용자 권한.
 *
 * <p>현재 범위에서는 인가 분기에 사용하지 않으며 모든 사용자가 {@code USER}로 가입한다. 관리자 등 권한 확장이 필요해지는 시점에 상수를 추가한다 (PRD 8.2
 * — "권한 확장 대비, 이번 범위 미사용").
 */
public enum Role {
    USER
}

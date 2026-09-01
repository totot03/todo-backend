package com.example.common.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 목록 응답 공통 래퍼. API_SPEC.md 3.1. Spring {@code Page}를 그대로 직렬화하지 않고 이 타입으로 변환한다(CLAUDE.md 불변 규칙 —
 * 페이지네이션).
 *
 * @param page 0-based 페이지 번호. UI는 1-based이므로 프론트엔드에서 변환한다
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** 엔티티 Page를 응답 DTO로 매핑하면서 동시에 PageResponse로 감싼다. 서비스 계층이 주로 쓰는 진입점이다. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}

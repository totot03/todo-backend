package com.example.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.common.response.PageResponse;
import com.example.common.sanitize.HtmlSanitizer;
import com.example.dto.todo.TodoCreateRequest;
import com.example.dto.todo.TodoResponse;
import com.example.dto.todo.TodoUpdateRequest;
import com.example.entity.Priority;
import com.example.entity.Todo;
import com.example.entity.User;
import com.example.repository.TodoRepository;

/**
 * 할 일 CRUD 비즈니스 로직. API_SPEC.md 3장.
 *
 * <p>조회·수정·토글·삭제는 전부 {@link #findOwned}로 소유권을 검증한 뒤 처리한다 — 없거나 타인 소유면 원인을 구분하지 않고 {@code
 * TODO_NOT_FOUND}(404)로 응답한다(CLAUDE.md 불변 규칙 — 리소스 존재 여부 비노출).
 */
@Service
public class TodoService {

    private final TodoRepository todoRepository;
    private final HtmlSanitizer htmlSanitizer;

    public TodoService(TodoRepository todoRepository, HtmlSanitizer htmlSanitizer) {
        this.todoRepository = todoRepository;
        this.htmlSanitizer = htmlSanitizer;
    }

    /**
     * 정렬 필드는 {@code createdAt} 하나로 고정한다 — 임의 필드 정렬을 허용하면 인덱스 없는 컬럼에 대한 정렬 요청으로 성능 저하나 오류를 유발할 수 있어,
     * {@link #resolveSort}에서 화이트리스트로 방향(asc/desc)만 받는다.
     */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    /**
     * 정렬 필드는 {@code createdAt}으로 고정하고 방향(최신순/오래된순)만 {@code sort} 파라미터로 받는다 — {@code Pageable}을
     * 컨트롤러에서 직접 받지 않고 문자열로 받아 여기서 화이트리스트 검증하는 이유다(FR-T06).
     *
     * <p>{@code completed}/{@code keyword}가 없을 때 리포지토리에 null을 그대로 넘기지 않고 "필터 적용 여부" boolean과 "값"을
     * 분리해서 넘긴다 — {@link TodoRepository#search}의 Javadoc 참고(PostgreSQL의 null 파라미터 타입 추론 문제 회피).
     */
    @Transactional(readOnly = true)
    public PageResponse<TodoResponse> list(
            Long userId, int page, int size, Boolean completed, String keyword, String sort) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword : null;
        Page<Todo> result =
                todoRepository.search(
                        userId,
                        completed != null,
                        completed != null && completed,
                        normalizedKeyword != null,
                        normalizedKeyword != null ? normalizedKeyword : "",
                        pageable);
        return PageResponse.from(result, TodoResponse::from);
    }

    /** {@code "createdAt,asc"}만 오름차순으로 인정하고, 그 외(필드명이 다르거나 형식이 잘못된 값 포함)는 전부 기본 정렬로 대체한다. */
    private Sort resolveSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", 2);
        boolean isCreatedAtAsc =
                parts.length == 2
                        && "createdAt".equals(parts[0].trim())
                        && "asc".equalsIgnoreCase(parts[1].trim());
        return isCreatedAtAsc ? Sort.by(Sort.Direction.ASC, "createdAt") : DEFAULT_SORT;
    }

    /**
     * priority 생략 시 MEDIUM으로 채운다. {@code Todo.builder().priority(null)}을 그대로 넘기면
     * {@code @Builder.Default}가 무시되고 실제 null이 저장되는 Lombok 함정이 있어, 여기서 명시적으로 계산한다.
     */
    @Transactional
    public TodoResponse create(User user, TodoCreateRequest request) {
        Priority priority = request.priority() != null ? request.priority() : Priority.MEDIUM;
        Todo todo =
                Todo.builder()
                        .user(user)
                        .title(request.title())
                        .description(htmlSanitizer.sanitize(request.description()))
                        .dueDate(request.dueDate())
                        .priority(priority)
                        .build();
        todoRepository.save(todo);
        return TodoResponse.from(todo);
    }

    @Transactional(readOnly = true)
    public TodoResponse get(Long id, Long userId) {
        return TodoResponse.from(findOwned(id, userId));
    }

    /** 요청에 담긴 필드만 반영하고, 나머지는 기존 값을 유지한다. */
    @Transactional
    public TodoResponse update(Long id, Long userId, TodoUpdateRequest request) {
        Todo todo = findOwned(id, userId);

        String title = request.title() != null ? request.title() : todo.getTitle();
        String description =
                request.description() != null
                        ? htmlSanitizer.sanitize(request.description())
                        : todo.getDescription();
        LocalDate dueDate = request.dueDate() != null ? request.dueDate() : todo.getDueDate();
        Priority priority = request.priority() != null ? request.priority() : todo.getPriority();

        // 영속 상태 엔티티이므로 트랜잭션 커밋 시점에 변경 감지(dirty checking)로 UPDATE가 나간다. save() 재호출 불필요.
        todo.update(title, description, dueDate, priority);
        return TodoResponse.from(todo);
    }

    @Transactional
    public TodoResponse toggle(Long id, Long userId) {
        Todo todo = findOwned(id, userId);
        todo.toggleComplete();
        return TodoResponse.from(todo);
    }

    /** 물리 삭제 금지, {@code deleted_at} 기록(Soft Delete). */
    @Transactional
    public void delete(Long id, Long userId) {
        Todo todo = findOwned(id, userId);
        todo.markDeleted();
    }

    private Todo findOwned(Long id, Long userId) {
        return todoRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
    }
}

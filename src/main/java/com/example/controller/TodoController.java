package com.example.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResponse;
import com.example.dto.todo.TodoCreateRequest;
import com.example.dto.todo.TodoResponse;
import com.example.dto.todo.TodoUpdateRequest;
import com.example.security.CustomUserDetails;
import com.example.service.TodoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 할 일 CRUD 6개 엔드포인트. API_SPEC.md 3장. 전부 인증 필요 + 소유권 스코프.
 *
 * <p>{@code io.swagger.v3.oas.annotations.responses.ApiResponse}는 이 프로젝트의 {@link ApiResponse}와
 * simple name이 충돌하므로 쓰지 않는다({@code @Tag}/{@code @Operation}만 사용).
 */
@Tag(name = "Todo", description = "할 일 CRUD API")
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @Operation(summary = "할 일 목록 조회 (페이지네이션 · 완료 필터 · 키워드 검색 · 정렬)")
    @GetMapping
    public ApiResponse<PageResponse<TodoResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(
                todoService.list(userDetails.getUserId(), page, size, completed, keyword, sort));
    }

    @Operation(summary = "할 일 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TodoResponse> create(
            @Valid @RequestBody TodoCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(todoService.create(userDetails.getUser(), request));
    }

    @Operation(summary = "할 일 상세 조회")
    @GetMapping("/{id}")
    public ApiResponse<TodoResponse> get(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(todoService.get(id, userDetails.getUserId()));
    }

    @Operation(summary = "할 일 부분 수정 (title/description/dueDate/priority만, completed는 toggle 전용)")
    @PatchMapping("/{id}")
    public ApiResponse<TodoResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TodoUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(todoService.update(id, userDetails.getUserId(), request));
    }

    @Operation(summary = "완료 상태 토글")
    @PatchMapping("/{id}/toggle")
    public ApiResponse<TodoResponse> toggle(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(todoService.toggle(id, userDetails.getUserId()));
    }

    @Operation(summary = "할 일 삭제 (Soft Delete)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        todoService.delete(id, userDetails.getUserId());
        return ApiResponse.success(null);
    }
}

package com.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

/** TodoService의 CRUD 로직을 검증한다. 특히 소유권 불일치가 전부 TODO_NOT_FOUND로 흡수되는지 확인한다. */
class TodoServiceTest {

    private final TodoRepository todoRepository = mock(TodoRepository.class);
    private final HtmlSanitizer htmlSanitizer = new HtmlSanitizer();
    private final TodoService todoService = new TodoService(todoRepository, htmlSanitizer);
    private final User owner = User.builder().id(1L).build();

    @Test
    void createFillsMediumPriorityWhenOmitted() {
        when(todoRepository.save(any(Todo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TodoCreateRequest request = new TodoCreateRequest("제목", null, null, null);

        TodoResponse response = todoService.create(owner, request);

        assertEquals("MEDIUM", response.priority());
    }

    @Test
    void createSanitizesDescriptionBeforeSaving() {
        when(todoRepository.save(any(Todo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TodoCreateRequest request =
                new TodoCreateRequest("제목", "<p>본문</p><script>alert(1)</script>", null, null);

        ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
        todoService.create(owner, request);
        verify(todoRepository).save(captor.capture());

        assertEquals("<p>본문</p>", captor.getValue().getDescription());
    }

    @Test
    void getThrowsTodoNotFoundWhenNotOwned() {
        when(todoRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> todoService.get(1L, 1L));

        assertEquals(ErrorCode.TODO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateThrowsTodoNotFoundWhenNotOwned() {
        when(todoRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
        TodoUpdateRequest request = new TodoUpdateRequest("수정", null, null, null);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> todoService.update(1L, 1L, request));

        assertEquals(ErrorCode.TODO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void toggleThrowsTodoNotFoundWhenNotOwned() {
        when(todoRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> todoService.toggle(1L, 1L));

        assertEquals(ErrorCode.TODO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteThrowsTodoNotFoundWhenNotOwned() {
        when(todoRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> todoService.delete(1L, 1L));

        assertEquals(ErrorCode.TODO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateKeepsOmittedFieldsUnchanged() {
        Todo todo =
                Todo.builder()
                        .id(1L)
                        .user(owner)
                        .title("원래 제목")
                        .description("원래 설명")
                        .dueDate(LocalDate.of(2026, 1, 1))
                        .priority(Priority.LOW)
                        .build();
        when(todoRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(todo));
        TodoUpdateRequest request = new TodoUpdateRequest("새 제목", null, null, null);

        TodoResponse response = todoService.update(1L, 1L, request);

        assertEquals("새 제목", response.title());
        assertEquals("원래 설명", response.description());
        assertEquals(LocalDate.of(2026, 1, 1), response.dueDate());
        assertEquals("LOW", response.priority());
    }

    @Test
    void toggleTwiceReturnsToOriginalState() {
        Todo todo = Todo.builder().id(1L).user(owner).title("제목").completed(false).build();
        when(todoRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(todo));

        todoService.toggle(1L, 1L);
        assertTrue(todo.isCompleted());

        todoService.toggle(1L, 1L);
        assertFalse(todo.isCompleted());
    }

    @Test
    void listNormalizesBlankKeywordAndAbsentCompletedToNoFilter() {
        when(todoRepository.search(
                        eq(1L), eq(false), eq(false), eq(false), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<Todo>(List.of()));

        PageResponse<TodoResponse> response = todoService.list(1L, 0, 10, null, "   ", null);

        assertTrue(response.content().isEmpty());
        verify(todoRepository)
                .search(eq(1L), eq(false), eq(false), eq(false), eq(""), any(Pageable.class));
    }

    /**
     * FR-T06 — 정렬 필드는 {@code createdAt} 하나로 고정하고 방향만 화이트리스트로 받는다. DB 없이도(TodoServiceTest는 Mockito
     * 목이라 통합 테스트와 달리 DB_PASSWORD 없이 실행된다) {@code resolveSort}의 분기를 전부 검증한다.
     */
    @Test
    void listResolvesSortWhitelistAndFallsBackToDefaultOtherwise() {
        when(todoRepository.search(
                        eq(1L), eq(false), eq(false), eq(false), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<Todo>(List.of()));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        todoService.list(1L, 0, 10, null, null, "createdAt,asc");
        todoService.list(1L, 0, 10, null, null, "createdAt,desc");
        todoService.list(1L, 0, 10, null, null, "title,asc"); // 허용되지 않은 필드
        todoService.list(1L, 0, 10, null, null, "createdAt,ASC"); // 대소문자 무시
        todoService.list(1L, 0, 10, null, null, null); // 미지정 시 기본값

        verify(todoRepository, times(5))
                .search(eq(1L), eq(false), eq(false), eq(false), eq(""), captor.capture());
        List<Pageable> pageables = captor.getAllValues();
        assertEquals(Sort.by(Sort.Direction.ASC, "createdAt"), pageables.get(0).getSort());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageables.get(1).getSort());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageables.get(2).getSort());
        assertEquals(Sort.by(Sort.Direction.ASC, "createdAt"), pageables.get(3).getSort());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageables.get(4).getSort());
    }
}

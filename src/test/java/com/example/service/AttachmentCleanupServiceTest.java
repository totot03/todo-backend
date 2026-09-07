package com.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.entity.Attachment;
import com.example.repository.AttachmentRepository;
import com.example.repository.TodoRepository;
import com.example.service.storage.FileStorageService;

/** 고아 파일 정리의 삭제 직전 가드와 부분 실패 격리를 검증한다 (M7). */
class AttachmentCleanupServiceTest {

    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final TodoRepository todoRepository = mock(TodoRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final AttachmentCleanupService cleanupService =
            new AttachmentCleanupService(attachmentRepository, todoRepository, fileStorageService);

    @Test
    void deletesOrphanCandidateWithNoActiveReference() {
        Attachment orphan =
                Attachment.builder()
                        .uuid(UUID.randomUUID())
                        .storageKey("todos/1/2026/09/a.png")
                        .build();
        when(attachmentRepository.findByTodoIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(orphan));
        when(todoRepository.countActiveReferencing(anyString())).thenReturn(0L);

        int deleted = cleanupService.cleanupOrphans();

        assertEquals(1, deleted);
        verify(fileStorageService).delete("todos/1/2026/09/a.png");
        assertNotNull(orphan.getDeletedAt());
    }

    @Test
    void skipsCandidateStillReferencedByActiveTodo() {
        // todo_id는 null이지만, 다른 활성 Todo가 본문에 같은 uuid를 여전히 참조 중인 경합 상황(가드가 막아야 하는 케이스).
        Attachment candidate =
                Attachment.builder()
                        .uuid(UUID.randomUUID())
                        .storageKey("todos/1/2026/09/still-referenced.png")
                        .build();
        when(attachmentRepository.findByTodoIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(candidate));
        when(todoRepository.countActiveReferencing(anyString())).thenReturn(1L);

        int deleted = cleanupService.cleanupOrphans();

        assertEquals(0, deleted);
        verify(fileStorageService, never()).delete(anyString());
        assertNull(candidate.getDeletedAt());
    }

    @Test
    void softDeletesRowEvenWhenBlobDeletionFails() {
        // blob 삭제 실패가 이 행 하나 때문에 DB soft delete까지 막으면 안 된다.
        Attachment orphan =
                Attachment.builder()
                        .uuid(UUID.randomUUID())
                        .storageKey("todos/1/2026/09/b.png")
                        .build();
        when(attachmentRepository.findByTodoIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(orphan));
        when(todoRepository.countActiveReferencing(anyString())).thenReturn(0L);
        doThrow(new RuntimeException("디스크 I/O 오류")).when(fileStorageService).delete(anyString());

        int deleted = cleanupService.cleanupOrphans();

        assertEquals(1, deleted);
        assertNotNull(orphan.getDeletedAt());
    }

    @Test
    void oneFailingCandidateDoesNotBlockTheRestOfTheBatch() {
        Attachment failing =
                Attachment.builder()
                        .uuid(UUID.randomUUID())
                        .storageKey("todos/1/2026/09/fail.png")
                        .build();
        Attachment healthy =
                Attachment.builder()
                        .uuid(UUID.randomUUID())
                        .storageKey("todos/1/2026/09/ok.png")
                        .build();
        when(attachmentRepository.findByTodoIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(failing, healthy));
        when(todoRepository.countActiveReferencing(anyString())).thenReturn(0L);
        doThrow(new RuntimeException("디스크 I/O 오류"))
                .when(fileStorageService)
                .delete("todos/1/2026/09/fail.png");

        int deleted = cleanupService.cleanupOrphans();

        assertEquals(2, deleted);
        assertNotNull(failing.getDeletedAt());
        assertNotNull(healthy.getDeletedAt());
        verify(fileStorageService, times(1)).delete("todos/1/2026/09/ok.png");
    }

    @Test
    void returnsZeroWhenNoCandidates() {
        when(attachmentRepository.findByTodoIsNullAndUpdatedAtBefore(any())).thenReturn(List.of());

        assertEquals(0, cleanupService.cleanupOrphans());
        verify(todoRepository, never()).countActiveReferencing(anyString());
    }

    @Test
    void queriesCandidatesOlderThan24Hours() {
        when(attachmentRepository.findByTodoIsNullAndUpdatedAtBefore(any())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusHours(24).minusSeconds(5);
        cleanupService.cleanupOrphans();
        LocalDateTime after = LocalDateTime.now().minusHours(24).plusSeconds(5);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(attachmentRepository).findByTodoIsNullAndUpdatedAtBefore(captor.capture());
        LocalDateTime usedThreshold = captor.getValue();

        assertTrue(usedThreshold.isAfter(before));
        assertTrue(usedThreshold.isBefore(after));
    }
}

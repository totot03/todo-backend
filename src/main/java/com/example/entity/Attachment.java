package com.example.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLRestriction;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드된 이미지 첨부 (M7 / FR-T14).
 *
 * <p>{@code todo}가 null인 행은 "작성 중에 올렸지만 아직 어느 할 일에도 붙지 않은 파일"이다. 사용자가 저장하지 않고 나가면 그대로 남으므로 고아 정리
 * 대상이 된다.
 *
 * <p><b>주의.</b> {@link Todo}에도 {@code @SQLRestriction}이 걸려 있어, 소프트 삭제된 할 일을 가리키는 첨부에서 {@link
 * #getTodo()} 프록시를 초기화하면 행이 걸러져 예외가 난다. 링크 해제·고아 정리는 연관을 타고 들어가지 말고 {@code
 * AttachmentRepository.findByTodoId(...)}처럼 FK 값으로만 다룬다.
 */
@Entity
@Table(
        name = "attachments",
        indexes = {
            @Index(name = "ix_attachments_user_deleted", columnList = "user_id, deleted_at"),
            @Index(name = "ix_attachments_todo", columnList = "todo_id")
        })
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Attachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부 노출용 식별자. PK는 연속된 정수라 URL에 쓰면 타인 파일의 존재 여부를 열거할 수 있으므로 UUID를 노출한다. */
    @Column(nullable = false, unique = true)
    private UUID uuid;

    /** 소유자. 조회 시 소유권 검증 기준이며, 불일치는 403이 아니라 404로 응답한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 붙어 있는 할 일. 작성 중 업로드는 아직 대상 할 일이 없으므로 null을 허용한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id")
    private Todo todo;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 10)
    private StorageType storageType;

    /** 저장 위치. 로컬은 base-dir 기준 상대경로, S3는 객체 키다. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    /** 표시용 원본 파일명. 경로 조작 문자가 섞일 수 있으므로 경로 생성에는 절대 쓰지 않는다. */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /** 매직 바이트로 판정한 MIME 타입. 클라이언트가 보낸 Content-Type 헤더는 신뢰하지 않는다. */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /**
     * 이 첨부를 할 일에 붙인다.
     *
     * <p>{@link Todo#update}와 같은 원칙이다 — 어떤 첨부를 어느 할 일에 붙일지는 서비스 계층이 계산하고, 엔티티는 넘겨받은 값으로 참조를 교체하기만
     * 한다.
     */
    public void linkTo(Todo todo) {
        this.todo = todo;
    }

    /** 할 일에서 떼어 낸다. 본문에서 이미지가 지워진 첨부를 고아 상태로 되돌릴 때 쓴다. */
    public void unlink() {
        this.todo = null;
    }
}

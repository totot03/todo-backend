package com.example.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.common.response.ApiResponse;
import com.example.dto.file.ImageUploadResponse;
import com.example.entity.Attachment;
import com.example.entity.StorageType;
import com.example.security.CustomUserDetails;
import com.example.service.AttachmentService;
import com.example.service.storage.FileStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 이미지 첨부 업로드·조회 2개 엔드포인트. FEATURE_IMAGE_UPLOAD.md 3장. 전부 인증 필요 + 소유권 스코프.
 *
 * <p>{@code io.swagger.v3.oas.annotations.responses.ApiResponse}는 이 프로젝트의 {@link ApiResponse}와
 * simple name이 충돌하므로 쓰지 않는다({@code @Tag}/{@code @Operation}만 사용) — {@link TodoController}와 같은 이유다.
 *
 * <p><b>{@link #download}만 {@code ResponseEntity<Resource>}를 반환한다(응답 봉투 예외).</b> 이 프로젝트의 다른 모든
 * 엔드포인트는 {@code ApiResponse<T>}를 직접 반환하지만, 이 엔드포인트의 성공 응답은 이미지 바이너리 스트림 또는 S3 리다이렉트라 JSON 봉투에 담을
 * 대상이 아니다. 실패 응답(401/404)은 {@link com.example.common.exception.GlobalExceptionHandler}를 그대로 거치므로 기존
 * 봉투를 유지한다(API_SPEC.md 3.1).
 *
 * <p>{@code /api/files/**}는 {@code SecurityConfig}의 {@code permitAll} 목록에 없다 — 인증 필수 엔드포인트이므로
 * {@code anyRequest().authenticated()}에 걸리는 것이 의도된 동작이다.
 */
@Tag(name = "File", description = "이미지 첨부 업로드·조회 API")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;

    public FileController(
            AttachmentService attachmentService, FileStorageService fileStorageService) {
        this.attachmentService = attachmentService;
        this.fileStorageService = fileStorageService;
    }

    @Operation(summary = "이미지 업로드")
    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImageUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(attachmentService.upload(file, userDetails.getUser()));
    }

    /**
     * 이미지를 내려준다. 로컬 저장소는 직접 스트리밍하고, S3는 presigned URL로 302 리다이렉트한다({@link
     * FileStorageService#getRedirectUrl} 참고). S3 구현은 v1.1에서 붙이므로 지금은 분기만 존재한다.
     *
     * <p>{@code Content-Type}은 요청 헤더가 아니라 업로드 시점에 매직 바이트로 판정해 DB에 저장해 둔 값을 쓴다. 클라이언트가 보낸 값을 그대로
     * 반사하면 저장된 파일이 이미지가 아니라 판정 실패 케이스였을 때도 임의의 Content-Type을 자처하게 된다.
     */
    @Operation(summary = "이미지 조회")
    @GetMapping("/{uuid}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID uuid, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Attachment attachment = attachmentService.findOwned(uuid, userDetails.getUserId());

        if (attachment.getStorageType() == StorageType.S3) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(
                            HttpHeaders.LOCATION,
                            fileStorageService.getRedirectUrl(attachment.getStorageKey()))
                    .build();
        }

        Resource resource = fileStorageService.load(attachment.getStorageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(resource);
    }
}

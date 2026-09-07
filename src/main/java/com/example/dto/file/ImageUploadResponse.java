package com.example.dto.file;

import com.example.entity.Attachment;

/**
 * 이미지 업로드 응답. FEATURE_IMAGE_UPLOAD.md 3.2.
 *
 * <p>{@code url}을 서버가 만들어 내려주는 이유는 에디터가 그대로 {@code <img src>}에 넣을 값이기 때문이다. 클라이언트가 uuid로 경로를 조립하게
 * 두면 저장 위치나 경로 규칙이 바뀔 때 프론트엔드까지 함께 고쳐야 한다.
 *
 * <p>{@code storageKey}는 응답에 넣지 않는다 — 디스크 구조나 S3 객체 키를 외부에 알릴 이유가 없다.
 */
public record ImageUploadResponse(String uuid, String url, String filename, long size) {

    public static ImageUploadResponse from(Attachment attachment) {
        String uuid = attachment.getUuid().toString();
        return new ImageUploadResponse(
                uuid,
                "/api/files/" + uuid,
                attachment.getOriginalFilename(),
                attachment.getFileSize());
    }
}

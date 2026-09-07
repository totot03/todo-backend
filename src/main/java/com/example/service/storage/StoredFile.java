package com.example.service.storage;

import com.example.entity.StorageType;

/**
 * 저장이 끝난 파일의 위치 정보. {@link FileStorageService#store}의 반환값이다.
 *
 * <p>저장소 종류를 함께 돌려주므로 서비스 계층이 {@code getType()}을 따로 묻지 않고 이 값만으로 {@code Attachment}를 만들 수 있다.
 *
 * @param storageType 실제로 저장한 저장소 종류
 * @param storageKey 저장 위치. 로컬은 base-dir 기준 상대경로, S3는 객체 키
 * @param size 저장된 바이트 수
 */
public record StoredFile(StorageType storageType, String storageKey, long size) {}

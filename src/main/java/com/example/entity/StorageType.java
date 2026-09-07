package com.example.entity;

/**
 * 파일이 실제로 저장된 위치. 레코드마다 자기 위치를 기억하기 위한 값이다.
 *
 * <p>로컬에 올린 파일과 S3에 올린 파일이 한 테이블에 섞여도 조회가 깨지지 않으려면, 저장소 종류를 전역 설정({@code file.storage.type})이 아니라 각
 * 행에서 읽어야 한다. 설정을 바꾸는 순간 과거 파일의 위치까지 함께 바뀌어 버리기 때문이다. S3 구현은 v1.1에서 붙인다.
 */
public enum StorageType {
    LOCAL,
    S3
}

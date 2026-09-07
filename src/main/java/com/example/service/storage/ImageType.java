package com.example.service.storage;

/**
 * 허용하는 이미지 형식. 매직 바이트 판정 결과이며, 확장자와 content-type을 함께 들고 있어 서버가 파일명과 응답 헤더를 모두 스스로 결정한다.
 *
 * <p>클라이언트가 보낸 확장자나 {@code Content-Type} 헤더는 어느 쪽도 저장에 쓰지 않는다. 둘 다 요청자가 자유롭게 적을 수 있는 값이라, 그대로 믿으면
 * {@code .png}로 위장한 스크립트가 그 이름 그대로 디스크에 남고 나중에 같은 이름으로 되돌려 주게 된다.
 *
 * <p>SVG는 목록에 없다. XML 문서라 {@code <script>}를 품을 수 있어 이미지로 취급하면 저장형 XSS 경로가 된다.
 */
public enum ImageType {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String contentType;

    ImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }
}

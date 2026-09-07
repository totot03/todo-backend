package com.example.service.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;

/** 매직 바이트 판정이 허용 형식 4종만 통과시키는지 검증한다 (NFR-S09). */
class ImageTypeDetectorTest {

    private final ImageTypeDetector detector = new ImageTypeDetector();

    @Test
    void detectsJpegBySignature() {
        assertEquals(ImageType.JPEG, detector.detect(fileOf(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)));
    }

    @Test
    void detectsPngBySignature() {
        assertEquals(
                ImageType.PNG,
                detector.detect(fileOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)));
    }

    @Test
    void detectsGifBySignature() {
        assertEquals(ImageType.GIF, detector.detect(fileOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)));
    }

    @Test
    void detectsWebpByRiffContainerAndMarker() {
        // RIFF 컨테이너는 offset 4~7이 파일 크기라 형식을 구분해 주지 않는다. offset 8의 WEBP 표식까지 봐야 한다.
        assertEquals(
                ImageType.WEBP,
                detector.detect(
                        fileOf(
                                0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42,
                                0x50)));
    }

    @Test
    void rejectsRiffContainerThatIsNotWebp() {
        // WAV도 RIFF로 시작한다. 선두 4바이트만 봤다면 통과했을 입력이다.
        assertThrows(
                BusinessException.class,
                () ->
                        detector.detect(
                                fileOf(
                                        0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x41,
                                        0x56, 0x45)));
    }

    @Test
    void rejectsTextFileDisguisedWithImageNameAndContentType() {
        MockMultipartFile disguised =
                new MockMultipartFile(
                        "file", "악성.png", "image/png", "<script>alert(1)</script>".getBytes());

        BusinessException e =
                assertThrows(BusinessException.class, () -> detector.detect(disguised));

        assertEquals(ErrorCode.INVALID_FILE_TYPE, e.getErrorCode());
    }

    @Test
    void rejectsSvgEvenThoughBrowsersRenderIt() {
        MockMultipartFile svg =
                new MockMultipartFile(
                        "file", "도형.svg", "image/svg+xml", "<svg xmlns=\"\"></svg>".getBytes());

        assertThrows(BusinessException.class, () -> detector.detect(svg));
    }

    @Test
    void rejectsEmptyFile() {
        assertThrows(
                BusinessException.class,
                () -> detector.detect(new MockMultipartFile("file", new byte[0])));
    }

    private MockMultipartFile fileOf(int... unsignedBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int b : unsignedBytes) {
            out.write(b);
        }
        return new MockMultipartFile("file", out.toByteArray());
    }
}

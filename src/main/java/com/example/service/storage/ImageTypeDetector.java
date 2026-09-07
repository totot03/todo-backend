package com.example.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;

/**
 * 파일 선두의 매직 바이트로 이미지 형식을 판정한다 (NFR-S09).
 *
 * <p>확장자도 {@code MultipartFile.getContentType()}도 보지 않는다. 둘 다 클라이언트가 보낸 문자열일 뿐이어서, 실행 가능한 내용을
 * {@code .png}라는 이름으로 올리는 것을 전혀 막지 못한다.
 *
 * <p>{@code ImageIO.read()}로 판정하지 않는 이유는 JDK에 WebP 리더가 없어 <b>정상 webp를 거부</b>하기 때문이다. 판정 실패가 곧 형식
 * 미지원으로 이어지므로, 라이브러리가 아는 형식이 아니라 우리가 허용하기로 한 형식을 직접 확인한다.
 */
@Component
public class ImageTypeDetector {

    /** WebP 판정에 offset 8~11의 {@code WEBP} 표식까지 필요하므로 12바이트를 읽는다. */
    private static final int HEADER_LENGTH = 12;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF_SIGNATURE = {0x47, 0x49, 0x46, 0x38};
    private static final byte[] RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};
    private static final int WEBP_MARKER_OFFSET = 8;

    /**
     * 허용 목록에 없으면 {@code INVALID_FILE_TYPE}으로 거부한다.
     *
     * <p>{@link MultipartFile#getInputStream()}은 호출할 때마다 새 스트림을 돌려주므로, 여기서 선두를 읽어도 이후 저장 단계가 같은 파일을
     * 처음부터 다시 읽을 수 있다.
     */
    public ImageType detect(MultipartFile file) {
        byte[] header = readHeader(file);

        if (startsWith(header, JPEG_SIGNATURE, 0)) {
            return ImageType.JPEG;
        }
        if (startsWith(header, PNG_SIGNATURE, 0)) {
            return ImageType.PNG;
        }
        if (startsWith(header, GIF_SIGNATURE, 0)) {
            return ImageType.GIF;
        }
        if (startsWith(header, RIFF_SIGNATURE, 0)
                && startsWith(header, WEBP_MARKER, WEBP_MARKER_OFFSET)) {
            return ImageType.WEBP;
        }
        throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 읽지 못했습니다.", e);
        }
    }

    /** 헤더가 시그니처보다 짧으면(빈 파일 등) 일치하지 않는 것으로 본다. */
    private boolean startsWith(byte[] header, byte[] signature, int offset) {
        if (header.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}

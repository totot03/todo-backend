package com.example.common.sanitize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** HtmlSanitizer가 허용 태그 화이트리스트만 남기고 위험 요소를 제거하는지 검증한다 (NFR-S05, API_SPEC.md 3.2). */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void removesScriptTagAndItsContent() {
        String result = sanitizer.sanitize("<p>안전</p><script>alert(1)</script>");

        assertEquals("<p>안전</p>", result);
    }

    @Test
    void removesEventHandlerAttributeButKeepsTag() {
        String result = sanitizer.sanitize("<p onclick=\"alert(1)\">hi</p>");

        assertEquals("<p>hi</p>", result);
    }

    @Test
    void stripsDisallowedTagButKeepsTextForJavascriptUrl() {
        String result = sanitizer.sanitize("<a href=\"javascript:alert(1)\">link</a>");

        assertFalse(result.contains("<a"));
        assertFalse(result.contains("javascript:"));
        assertTrue(result.contains("link"));
    }

    @Test
    void keepsAllowedTagsUnchanged() {
        // jsoup은 pretty-print 과정에서 태그 사이 개행·들여쓰기(및 인라인 요소 사이 공백)를 임의로 넣거나 지우므로,
        // 모든 공백을 제거한 뒤 태그와 텍스트 내용만 비교한다(정확한 바이트 일치는 검증하지 않는다).
        String html =
                "<h1>제목</h1><p><strong>굵게</strong> <em>기울임</em></p>"
                        + "<ul><li>목록</li></ul><blockquote>인용</blockquote><code>코드</code>";

        String result = sanitizer.sanitize(html);

        assertEquals(html.replaceAll("\\s+", ""), result.replaceAll("\\s+", ""));
    }

    @Test
    void returnsNullForNullInput() {
        assertNull(sanitizer.sanitize(null));
    }
}

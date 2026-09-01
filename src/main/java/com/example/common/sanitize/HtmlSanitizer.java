package com.example.common.sanitize;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 리치 텍스트 description을 저장 전에 sanitize한다 (NFR-S05, API_SPEC.md 3.2).
 *
 * <p>허용 태그 외에는 태그·속성을 전부 제거한다. 어떤 태그에도 속성을 허용하지 않으므로({@code addAttributes} 호출이 없음) {@code on*} 이벤트
 * 핸들러나 {@code javascript:} URL이 살아남을 여지가 구조적으로 없다. Todo 생성/수정 두 경로 모두 이 컴포넌트 하나만 거치게 해서 sanitize
 * 규칙이 두 곳에서 갈라지지 않게 한다.
 */
@Component
public class HtmlSanitizer {

    private static final Safelist DESCRIPTION_SAFELIST =
            Safelist.none()
                    .addTags(
                            "p",
                            "br",
                            "strong",
                            "em",
                            "u",
                            "s",
                            "h1",
                            "h2",
                            "h3",
                            "ul",
                            "ol",
                            "li",
                            "blockquote",
                            "code");

    /** 허용 태그 화이트리스트로 정제한다. {@code null}은 그대로 {@code null}을 반환한다(description은 선택 필드). */
    public String sanitize(String rawHtml) {
        if (rawHtml == null) {
            return null;
        }
        return Jsoup.clean(rawHtml, DESCRIPTION_SAFELIST);
    }
}

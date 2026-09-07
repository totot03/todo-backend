package com.example.common.sanitize;

import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * 리치 텍스트 description을 저장 전에 sanitize한다 (NFR-S05, API_SPEC.md 3.2).
 *
 * <p>허용 태그 외에는 태그·속성을 전부 제거한다. {@code img}를 제외한 모든 태그는 여전히 속성을 전혀 허용하지 않으므로({@code addAttributes}
 * 호출이 없음) {@code on*} 이벤트 핸들러나 {@code javascript:} URL이 그 태그들에 살아남을 여지는 구조적으로 없다.
 *
 * <p><b>{@code img[src]}만 예외다 (M7).</b> jsoup의 {@code addProtocols}로 {@code src}를 검증하면 두 가지가 동시에
 * 틀린다 — 아예 걸지 않으면 {@code javascript:}/{@code data:}가 통과하고, 걸면 상대경로({@code /api/files/{uuid}})가
 * "프로토콜이 없다"는 이유로 통째로 잘려나간다. 그래서 jsoup의 프로토콜 검사에 맡기지 않고, {@link #IMAGE_SRC_PATTERN}으로 우리 파일 API 경로
 * 형태만 허용하는 <b>사후 화이트리스트</b>를 직접 적용한다. 이 정규식과 다르면 {@code remove()}로 요소를 통째로 없앤다 — {@code src} 속성만
 * 지우면 깨진 {@code img} 태그가 본문에 남기 때문이다. 외부 URL과 {@code data:} URI는 이 규칙에 걸려 자동으로 제거된다.
 *
 * <p>Todo 생성/수정 두 경로 모두 이 컴포넌트 하나만 거치게 해서 sanitize 규칙이 두 곳에서 갈라지지 않게 한다.
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
                            "code",
                            "img")
                    .addAttributes("img", "src", "alt", "width");

    /** 첨부 파일 조회 API 경로만 허용한다. UUID 형식까지 고정해 다른 상대경로가 끼어들 여지를 없앤다. */
    private static final Pattern IMAGE_SRC_PATTERN =
            Pattern.compile(
                    "^/api/files/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /** 허용 태그 화이트리스트로 정제한다. {@code null}은 그대로 {@code null}을 반환한다(description은 선택 필드). */
    public String sanitize(String rawHtml) {
        if (rawHtml == null) {
            return null;
        }
        String cleaned = Jsoup.clean(rawHtml, DESCRIPTION_SAFELIST);
        return removeImagesWithDisallowedSrc(cleaned);
    }

    /**
     * jsoup의 Safelist는 속성 "형태"만 검사할 뿐 값의 의미를 모른다. {@code addProtocols}에 맡기지 않고 우리 경로 형식을 직접 정규식으로
     * 재검증하는 이유가 여기에 있다 — {@code src}가 이 형식과 조금이라도 다르면(외부 URL, {@code javascript:}, {@code data:},
     * 다른 API 경로) 요소 자체를 제거한다.
     */
    private String removeImagesWithDisallowedSrc(String cleanedHtml) {
        Document doc = Jsoup.parseBodyFragment(cleanedHtml);
        Elements images = doc.body().select("img");
        for (Element img : images) {
            String src = img.attr("src");
            if (!IMAGE_SRC_PATTERN.matcher(src).matches()) {
                img.remove();
            }
        }
        return doc.body().html();
    }
}

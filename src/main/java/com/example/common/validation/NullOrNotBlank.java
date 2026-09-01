package com.example.common.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * "값이 없으면(null) 통과, 값이 있으면 공백 금지"를 표현하는 제약. 표준 {@code @NotBlank}는 null도 거부하기 때문에, PATCH처럼 "필드를 안
 * 보내면 변경 없음, 보내면 빈 값 금지"인 부분 수정 요청에는 쓸 수 없어 별도로 정의한다 (예: {@code TodoUpdateRequest}).
 */
@Constraint(validatedBy = NullOrNotBlankValidator.class)
@Target({
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.METHOD,
    ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface NullOrNotBlank {

    String message() default "값을 비워둘 수 없습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

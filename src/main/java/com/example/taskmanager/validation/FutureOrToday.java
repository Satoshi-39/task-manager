package com.example.taskmanager.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * カスタムバリデーションアノテーション：日付が今日以降であることを検証する。
 *
 * Java Gold トピック:
 * - カスタムアノテーション定義（@interface）
 * - メタアノテーション（@Target, @Retention, @Constraint, @Documented）
 * - アノテーション要素（message, groups, payload）
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = FutureOrTodayValidator.class)
public @interface FutureOrToday {

    String message() default "Date must be today or in the future";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.example.taskmanager.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @FutureOrToday アノテーションのバリデータ実装。
 *
 * Java Gold トピック:
 * - ConstraintValidator インタフェースの実装（Generics）
 * - Date/Time API（LocalDate, LocalDateTime）
 */
public class FutureOrTodayValidator implements ConstraintValidator<FutureOrToday, LocalDateTime> {

    @Override
    public void initialize(FutureOrToday constraintAnnotation) {
        // 初期化不要
    }

    @Override
    public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null許可は @NotNull で制御
        }
        LocalDate today = LocalDate.now();
        return !value.toLocalDate().isBefore(today);
    }
}

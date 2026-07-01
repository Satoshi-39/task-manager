package com.example.taskmanager.security;

import com.example.taskmanager.domain.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * SecurityContextHolder からユーザー情報を取得するユーティリティ。
 *
 * Java Gold トピック:
 * - Optional を使った null 安全なキャスト
 * - instanceof パターンマッチング (Java 16+)
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // ユーティリティクラスのインスタンス化を防止
    }

    /**
     * 現在のログインユーザーの CustomUserDetails を取得する。
     */
    public static Optional<CustomUserDetails> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return Optional.of(userDetails);
        }
        return Optional.empty();
    }

    /**
     * 現在のログインユーザーの ID を取得する。
     */
    public static Optional<Long> getCurrentUserId() {
        return getCurrentUser().map(CustomUserDetails::getUserId);
    }

    /**
     * 現在のユーザーが ADMIN ロールかどうかを判定する。
     */
    public static boolean isAdmin() {
        return getCurrentUser()
                .map(user -> user.getRole() == UserRole.ADMIN)
                .orElse(false);
    }
}

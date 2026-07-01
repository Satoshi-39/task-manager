package com.example.taskmanager.domain.enums;

/**
 * ユーザーのロールを表す Enum。
 *
 * Java Gold トピック:
 * - Enum のフィールド・コンストラクタ・メソッド定義
 * - Spring Security のロール制御で使用
 */
public enum UserRole {

    ADMIN("管理者"),
    USER("一般ユーザー");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

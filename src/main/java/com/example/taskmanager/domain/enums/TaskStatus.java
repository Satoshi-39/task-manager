package com.example.taskmanager.domain.enums;

/**
 * タスクの状態を表すEnum。
 * Enumのフィールド・コンストラクタ・メソッド定義（Java Gold範囲）を実践。
 */
public enum TaskStatus {

    TODO("未着手", false),
    IN_PROGRESS("進行中", false),
    DONE("完了", true),
    CANCELLED("中止", true);

    private final String displayName;
    private final boolean terminal;

    TaskStatus(String displayName, boolean terminal) {
        this.displayName = displayName;
        this.terminal = terminal;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 終了状態（DONE / CANCELLED）かどうかを返す。
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 指定ステータスへの遷移が許可されるかを判定する。
     * 終了状態からは遷移不可。TODO→DONE への直接遷移は不可。
     */
    public boolean canTransitionTo(TaskStatus next) {
        if (this == next) return false;
        if (this.terminal) return false;
        if (this == TODO && next == DONE) return false;
        return true;
    }
}

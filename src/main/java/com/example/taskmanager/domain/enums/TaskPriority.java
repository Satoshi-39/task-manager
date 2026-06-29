package com.example.taskmanager.domain.enums;

/**
 * タスクの優先度を表すEnum。
 * ordinal() に依存しない数値フィールドで優先度レベルを管理する。
 */
public enum TaskPriority {

    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高"),
    CRITICAL(4, "緊急");

    private final int level;
    private final String displayName;

    TaskPriority(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 数値レベルからEnumを逆引きする。
     * Stream API による検索（Java Gold範囲）。
     */
    public static TaskPriority fromLevel(int level) {
        for (TaskPriority p : values()) {
            if (p.level == level) return p;
        }
        throw new IllegalArgumentException("Unknown priority level: " + level);
    }
}

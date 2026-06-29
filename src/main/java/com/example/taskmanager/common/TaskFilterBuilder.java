package com.example.taskmanager.common;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.function.Predicate;

/**
 * Predicate<Task> を動的に組み立てるビルダー。
 *
 * Java Gold トピック:
 * - 関数型インタフェース（Predicate）
 * - Predicate の and() / or() 合成
 * - ビルダーパターン + Lambda
 */
public class TaskFilterBuilder {

    private Predicate<Task> filter = task -> true;

    public TaskFilterBuilder withStatus(TaskStatus status) {
        if (status != null) {
            filter = filter.and(task -> task.getStatus() == status);
        }
        return this;
    }

    public TaskFilterBuilder withPriority(TaskPriority priority) {
        if (priority != null) {
            filter = filter.and(task -> task.getPriority() == priority);
        }
        return this;
    }

    /**
     * タイトルまたは説明にキーワードを含むタスクをフィルタする。
     * or() による Predicate 合成。
     */
    public TaskFilterBuilder withKeyword(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.toLowerCase();
            Predicate<Task> titleMatch = task ->
                    task.getTitle().toLowerCase().contains(lowerKeyword);
            Predicate<Task> descMatch = task ->
                    task.getDescription() != null
                            && task.getDescription().toLowerCase().contains(lowerKeyword);
            filter = filter.and(titleMatch.or(descMatch));
        }
        return this;
    }

    public TaskFilterBuilder withOverdue(Boolean overdue) {
        if (overdue != null && overdue) {
            LocalDateTime now = LocalDateTime.now();
            filter = filter.and(task ->
                    task.getDueDate() != null
                            && task.getDueDate().isBefore(now)
                            && !task.getStatus().isTerminal());
        }
        return this;
    }

    public Predicate<Task> build() {
        return filter;
    }
}

package com.example.taskmanager.service;

import com.example.taskmanager.common.TaskEvent;
import com.example.taskmanager.common.TaskEventListener;
import com.example.taskmanager.domain.entity.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 非同期通知サービス（ログ出力でシミュレート）。
 *
 * <p>{@link TaskEventListener} を実装し、{@link com.example.taskmanager.common.TaskEventPublisher}
 * 経由でタスクイベントを受け取る。イベントを受け取ると CompletableFuture チェーンで
 * 非同期に通知処理を実行する。</p>
 *
 * Java Gold トピック:
 * <ul>
 *   <li>CompletableFuture（supplyAsync, thenApply, exceptionally）</li>
 *   <li>{@code @Async} による非同期実行</li>
 *   <li>TaskEventListener の実装（Observer パターンの具象 Observer）</li>
 * </ul>
 */
@Service
public class TaskNotificationService implements TaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(TaskNotificationService.class);

    /**
     * タスク作成通知を非同期で送信する。
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> notifyTaskCreated(Task task) {
        return CompletableFuture.supplyAsync(() -> {
                    log.info("[Notification] Preparing creation notification for task: {}",
                            task.getTaskNumber());
                    simulateDelay();
                    return task.getTaskNumber();
                })
                .thenApply(taskNumber -> {
                    log.info("[Notification] Task created notification sent: {}", taskNumber);
                    return taskNumber;
                })
                .exceptionally(ex -> {
                    log.error("[Notification] Failed to send creation notification", ex);
                    return null;
                })
                .thenAccept(result -> {
                    // CompletableFuture<Void> への変換
                });
    }

    /**
     * タスク完了通知を非同期で送信する。
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> notifyTaskCompleted(Task task) {
        return CompletableFuture.supplyAsync(() -> {
                    log.info("[Notification] Preparing completion notification for task: {}",
                            task.getTaskNumber());
                    simulateDelay();
                    return String.format("Task %s (%s) completed", task.getTaskNumber(), task.getTitle());
                })
                .thenApply(message -> {
                    log.info("[Notification] {}", message);
                    return message;
                })
                .exceptionally(ex -> {
                    log.error("[Notification] Failed to send completion notification", ex);
                    return null;
                })
                .thenAccept(result -> {});
    }

    /**
     * 期限切れ通知を非同期で送信する。
     * CompletableFuture チェーンの応用例。
     */
    @Async("taskExecutor")
    public CompletableFuture<String> notifyOverdue(Task task) {
        return CompletableFuture.supplyAsync(() -> {
                    log.info("[Notification] Checking overdue status for: {}", task.getTaskNumber());
                    simulateDelay();
                    return task;
                })
                .thenApply(t -> {
                    String message = String.format("OVERDUE: Task %s (%s) was due %s",
                            t.getTaskNumber(), t.getTitle(), t.getDueDate());
                    log.warn("[Notification] {}", message);
                    return message;
                })
                .exceptionally(ex -> {
                    log.error("[Notification] Failed to process overdue notification", ex);
                    return "Notification failed: " + ex.getMessage();
                });
    }

    private void simulateDelay() {
        try {
            Thread.sleep(100); // 通知処理のシミュレート
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- TaskEventListener 実装 ---

    @Override
    public void onTaskCreated(TaskEvent event) {
        notifyTaskCreated(event.getTask());
    }

    @Override
    public void onTaskUpdated(TaskEvent event) {
        log.info("[Notification] Task updated event received: {}", event.getTask().getTaskNumber());
    }

    @Override
    public void onTaskDeleted(TaskEvent event) {
        log.info("[Notification] Task deleted event received: {}", event.getTask().getTaskNumber());
    }
}

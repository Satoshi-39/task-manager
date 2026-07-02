package com.example.taskmanager.common;

import com.example.taskmanager.domain.entity.Task;

import java.time.LocalDateTime;

/**
 * タスクイベントデータ。
 *
 * <p>タスクの作成・更新・削除をイベントとして表現するデータクラス。
 * {@link TaskEventPublisher} から {@link TaskEventListener} に渡される。</p>
 *
 * Java Gold トピック:
 * <ul>
 *   <li>イベント駆動設計（Observer パターン）のデータ部分</li>
 *   <li>record ではなく class にしているのは Java Gold 試験範囲を考慮</li>
 * </ul>
 */
public class TaskEvent {

    /**
     * イベント種別。
     */
    public enum EventType {
        CREATED,
        UPDATED,
        DELETED
    }

    private final EventType eventType;
    private final Task task;
    private final LocalDateTime occurredAt;

    public TaskEvent(EventType eventType, Task task) {
        this.eventType = eventType;
        this.task = task;
        this.occurredAt = LocalDateTime.now();
    }

    public EventType getEventType() {
        return eventType;
    }

    public Task getTask() {
        return task;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return String.format("TaskEvent{type=%s, taskNumber=%s, occurredAt=%s}",
                eventType, task.getTaskNumber(), occurredAt);
    }
}

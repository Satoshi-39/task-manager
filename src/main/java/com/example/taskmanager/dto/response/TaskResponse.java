package com.example.taskmanager.dto.response;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public class TaskResponse implements Serializable {

    private final Long id;
    private final String taskNumber;
    private final String title;
    private final String description;
    private final TaskStatus status;
    private final String statusDisplayName;
    private final TaskPriority priority;
    private final String priorityDisplayName;
    private final LocalDateTime dueDate;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long version;

    private TaskResponse(Task task) {
        this.id = task.getId();
        this.taskNumber = task.getTaskNumber();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getStatus();
        this.statusDisplayName = task.getStatus().getDisplayName();
        this.priority = task.getPriority();
        this.priorityDisplayName = task.getPriority().getDisplayName();
        this.dueDate = task.getDueDate();
        this.createdAt = task.getCreatedAt();
        this.updatedAt = task.getUpdatedAt();
        this.version = task.getVersion();
    }

    public static TaskResponse from(Task task) {
        return new TaskResponse(task);
    }

    // --- Getters ---

    public Long getId() { return id; }
    public String getTaskNumber() { return taskNumber; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public String getStatusDisplayName() { return statusDisplayName; }
    public TaskPriority getPriority() { return priority; }
    public String getPriorityDisplayName() { return priorityDisplayName; }
    public LocalDateTime getDueDate() { return dueDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}

package com.example.taskmanager.dto.request;

import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.validation.FutureOrToday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class TaskCreateRequest {

    @NotBlank(message = "{task.title.notblank}")
    @Size(max = 200, message = "{task.title.size}")
    private String title;

    @Size(max = 2000, message = "{task.description.size}")
    private String description;

    private TaskPriority priority;

    @FutureOrToday(message = "{task.dueDate.futureOrToday}")
    private LocalDateTime dueDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
}

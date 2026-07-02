package com.example.taskmanager.batch.dto;

/**
 * CSV 1行分のタスクデータを表す POJO。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>Generics — {@code ItemProcessor<Task, TaskCsvRow>} の型引数として利用</li>
 *   <li>Date/Time API — 日時フィールドは文字列で保持し、変換時に DateTimeFormatter を使用</li>
 * </ul>
 */
public class TaskCsvRow {

    private String taskNumber;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String dueDate;
    private String createdAt;

    public TaskCsvRow() {
    }

    public TaskCsvRow(String taskNumber, String title, String description,
                      String status, String priority, String dueDate, String createdAt) {
        this.taskNumber = taskNumber;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
    }

    // --- Getters & Setters ---

    public String getTaskNumber() {
        return taskNumber;
    }

    public void setTaskNumber(String taskNumber) {
        this.taskNumber = taskNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}

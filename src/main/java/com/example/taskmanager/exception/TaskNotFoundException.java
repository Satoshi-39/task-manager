package com.example.taskmanager.exception;

public class TaskNotFoundException extends BusinessException {

    public TaskNotFoundException(Long id) {
        super("TASK_NOT_FOUND", "Task not found with id: " + id);
    }

    public TaskNotFoundException(String taskNumber) {
        super("TASK_NOT_FOUND", "Task not found with number: " + taskNumber);
    }
}

package com.example.taskmanager.exception;

public class TaskExportException extends BusinessException {

    public TaskExportException(String message, Throwable cause) {
        super("TASK_EXPORT_ERROR", message, cause);
    }
}

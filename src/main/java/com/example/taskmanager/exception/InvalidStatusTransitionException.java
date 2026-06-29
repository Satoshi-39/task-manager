package com.example.taskmanager.exception;

import com.example.taskmanager.domain.enums.TaskStatus;

public class InvalidStatusTransitionException extends BusinessException {

    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super("INVALID_STATUS_TRANSITION",
              String.format("Cannot transition from %s to %s", from, to));
    }
}

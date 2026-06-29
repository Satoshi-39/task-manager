package com.example.taskmanager.service;

import com.example.taskmanager.common.TaskNumberGenerator;
import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;
import com.example.taskmanager.dto.request.TaskCreateRequest;
import com.example.taskmanager.dto.request.TaskUpdateRequest;
import com.example.taskmanager.exception.InvalidStatusTransitionException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskService の単体テスト。
 *
 * Java Gold トピック:
 * - JUnit 5（@Nested, @DisplayName, assertAll, assertThrows）
 * - Mockito（@Mock, @InjectMocks, when/verify）
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskNumberGenerator taskNumberGenerator;

    @Mock
    private TaskNotificationService notificationService;

    @InjectMocks
    private TaskService taskService;

    @Nested
    @DisplayName("createTask")
    class CreateTask {

        @Test
        @DisplayName("正常にタスクを作成できる")
        void shouldCreateTaskSuccessfully() {
            // Arrange
            TaskCreateRequest request = new TaskCreateRequest();
            request.setTitle("Test Task");
            request.setDescription("Test Description");
            request.setPriority(TaskPriority.HIGH);

            when(taskNumberGenerator.generate()).thenReturn("TASK-20260101-0001");

            Task savedTask = new Task("TASK-20260101-0001", "Test Task", "Test Description",
                    TaskStatus.TODO, TaskPriority.HIGH, null);
            when(taskRepository.save(any(Task.class))).thenReturn(savedTask);
            when(notificationService.notifyTaskCreated(any()))
                    .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

            // Act
            Task result = taskService.createTask(request);

            // Assert
            assertAll(
                    () -> assertEquals("Test Task", result.getTitle()),
                    () -> assertEquals(TaskStatus.TODO, result.getStatus()),
                    () -> assertEquals(TaskPriority.HIGH, result.getPriority()),
                    () -> assertEquals("TASK-20260101-0001", result.getTaskNumber())
            );

            verify(taskRepository).save(any(Task.class));
            verify(notificationService).notifyTaskCreated(any(Task.class));
        }

        @Test
        @DisplayName("優先度未指定時はMEDIUMがデフォルト")
        void shouldDefaultToMediumPriority() {
            TaskCreateRequest request = new TaskCreateRequest();
            request.setTitle("No Priority Task");

            when(taskNumberGenerator.generate()).thenReturn("TASK-20260101-0002");

            Task savedTask = new Task("TASK-20260101-0002", "No Priority Task", null,
                    TaskStatus.TODO, TaskPriority.MEDIUM, null);
            when(taskRepository.save(any(Task.class))).thenReturn(savedTask);
            when(notificationService.notifyTaskCreated(any()))
                    .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

            Task result = taskService.createTask(request);

            assertEquals(TaskPriority.MEDIUM, result.getPriority());
        }
    }

    @Nested
    @DisplayName("getTask")
    class GetTask {

        @Test
        @DisplayName("存在するタスクを取得できる")
        void shouldReturnTask() {
            Task task = new Task("TASK-001", "Found", null,
                    TaskStatus.TODO, TaskPriority.LOW, null);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            Task result = taskService.getTask(1L);

            assertEquals("Found", result.getTitle());
        }

        @Test
        @DisplayName("存在しないIDでTaskNotFoundExceptionがスローされる")
        void shouldThrowWhenNotFound() {
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(TaskNotFoundException.class, () -> taskService.getTask(999L));
        }
    }

    @Nested
    @DisplayName("updateTask")
    class UpdateTask {

        @Test
        @DisplayName("有効なステータス遷移で更新成功")
        void shouldUpdateTaskWithValidTransition() {
            Task existing = new Task("TASK-001", "Original", "Desc",
                    TaskStatus.TODO, TaskPriority.MEDIUM, null);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TaskUpdateRequest request = new TaskUpdateRequest();
            request.setTitle("Updated");
            request.setDescription("Updated Desc");
            request.setStatus(TaskStatus.IN_PROGRESS);
            request.setVersion(0L);

            Task result = taskService.updateTask(1L, request);

            assertAll(
                    () -> assertEquals("Updated", result.getTitle()),
                    () -> assertEquals(TaskStatus.IN_PROGRESS, result.getStatus())
            );
        }

        @Test
        @DisplayName("無効なステータス遷移でInvalidStatusTransitionException")
        void shouldThrowOnInvalidTransition() {
            Task existing = new Task("TASK-001", "Done Task", "Desc",
                    TaskStatus.DONE, TaskPriority.MEDIUM, null);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

            TaskUpdateRequest request = new TaskUpdateRequest();
            request.setTitle("Updated");
            request.setStatus(TaskStatus.IN_PROGRESS);
            request.setVersion(0L);

            assertThrows(InvalidStatusTransitionException.class,
                    () -> taskService.updateTask(1L, request));
        }
    }

    @Nested
    @DisplayName("deleteTask")
    class DeleteTask {

        @Test
        @DisplayName("存在するタスクを削除できる")
        void shouldDeleteTask() {
            Task task = new Task("TASK-001", "To Delete", null,
                    TaskStatus.TODO, TaskPriority.LOW, null);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            taskService.deleteTask(1L);

            verify(taskRepository).delete(task);
        }

        @Test
        @DisplayName("存在しないタスクの削除でTaskNotFoundException")
        void shouldThrowWhenDeletingNonExistent() {
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(TaskNotFoundException.class, () -> taskService.deleteTask(999L));
        }
    }

    @Nested
    @DisplayName("集計メソッド")
    class AggregationMethods {

        @Test
        @DisplayName("calculateTotalPriorityScore - 未完了タスクの優先度合計")
        void shouldCalculatePriorityScore() {
            List<Task> tasks = List.of(
                    new Task("T1", "a", null, TaskStatus.TODO, TaskPriority.HIGH, null),
                    new Task("T2", "b", null, TaskStatus.IN_PROGRESS, TaskPriority.LOW, null),
                    new Task("T3", "c", null, TaskStatus.DONE, TaskPriority.CRITICAL, null) // 除外
            );
            when(taskRepository.findAll()).thenReturn(tasks);

            int score = taskService.calculateTotalPriorityScore();

            // HIGH(3) + LOW(1) = 4, DONE は除外
            assertEquals(4, score);
        }
    }
}

package com.example.taskmanager.service;

import com.example.taskmanager.common.TaskEvent;
import com.example.taskmanager.common.TaskEventPublisher;
import com.example.taskmanager.common.TaskNumberGenerator;
import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.entity.User;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;
import com.example.taskmanager.domain.enums.UserRole;
import com.example.taskmanager.dto.request.TaskCreateRequest;
import com.example.taskmanager.dto.request.TaskUpdateRequest;
import com.example.taskmanager.exception.InvalidStatusTransitionException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
 * - SecurityContext のセットアップとクリーンアップ
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskNumberGenerator taskNumberGenerator;

    @Mock
    private TaskNotificationService notificationService;

    @Mock
    private TaskCacheService cacheService;

    @Mock
    private TaskEventPublisher eventPublisher;

    @InjectMocks
    private TaskService taskService;

    /**
     * テスト用に SecurityContext に ADMIN ユーザーをセットする。
     */
    private void setSecurityContext(Long userId, String username, UserRole role) {
        User user = new User(username, "password", username, role);
        // リフレクションで ID をセット
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CustomUserDetails userDetails = new CustomUserDetails(user);
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void setUp() {
        // デフォルトは ADMIN ユーザー（既存テストの互換性維持）
        setSecurityContext(1L, "admin", UserRole.ADMIN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

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
            verify(cacheService).put(any(), any(Task.class));
            verify(eventPublisher).publish(any(TaskEvent.class));
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

            Task result = taskService.createTask(request);

            assertEquals(TaskPriority.MEDIUM, result.getPriority());
        }
    }

    @Nested
    @DisplayName("getTask")
    class GetTask {

        @Test
        @DisplayName("ADMINは全タスクを取得できる")
        void shouldReturnTaskForAdmin() {
            Task task = new Task("TASK-001", "Found", null,
                    TaskStatus.TODO, TaskPriority.LOW, null, 2L);
            when(cacheService.getOrLoad(1L)).thenReturn(Optional.of(task));

            Task result = taskService.getTask(1L);

            assertEquals("Found", result.getTitle());
        }

        @Test
        @DisplayName("USERは自分のタスクを取得できる")
        void shouldReturnOwnTaskForUser() {
            setSecurityContext(2L, "user1", UserRole.USER);
            Task task = new Task("TASK-001", "Own Task", null,
                    TaskStatus.TODO, TaskPriority.LOW, null, 2L);
            when(cacheService.getOrLoad(1L)).thenReturn(Optional.of(task));

            Task result = taskService.getTask(1L);

            assertEquals("Own Task", result.getTitle());
        }

        @Test
        @DisplayName("USERは他人のタスクにアクセスできない")
        void shouldDenyAccessToOtherUserTask() {
            setSecurityContext(2L, "user1", UserRole.USER);
            Task task = new Task("TASK-001", "Other's Task", null,
                    TaskStatus.TODO, TaskPriority.LOW, null, 3L);
            when(cacheService.getOrLoad(1L)).thenReturn(Optional.of(task));

            assertThrows(AccessDeniedException.class, () -> taskService.getTask(1L));
        }

        @Test
        @DisplayName("存在しないIDでTaskNotFoundExceptionがスローされる")
        void shouldThrowWhenNotFound() {
            when(cacheService.getOrLoad(999L)).thenReturn(Optional.empty());

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
                    TaskStatus.TODO, TaskPriority.MEDIUM, null, 1L);
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
                    TaskStatus.DONE, TaskPriority.MEDIUM, null, 1L);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

            TaskUpdateRequest request = new TaskUpdateRequest();
            request.setTitle("Updated");
            request.setStatus(TaskStatus.IN_PROGRESS);
            request.setVersion(0L);

            assertThrows(InvalidStatusTransitionException.class,
                    () -> taskService.updateTask(1L, request));
        }

        @Test
        @DisplayName("USERが他人のタスクを更新できない")
        void shouldDenyUpdateOtherUserTask() {
            setSecurityContext(2L, "user1", UserRole.USER);
            Task existing = new Task("TASK-001", "Other's Task", "Desc",
                    TaskStatus.TODO, TaskPriority.MEDIUM, null, 3L);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

            TaskUpdateRequest request = new TaskUpdateRequest();
            request.setTitle("Updated");
            request.setStatus(TaskStatus.IN_PROGRESS);

            assertThrows(AccessDeniedException.class,
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
                    TaskStatus.TODO, TaskPriority.LOW, null, 1L);
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
    @DisplayName("getAllTasks")
    class GetAllTasks {

        @Test
        @DisplayName("ADMINは全タスクを取得")
        void shouldReturnAllTasksForAdmin() {
            List<Task> tasks = List.of(
                    new Task("T1", "Task 1", null, TaskStatus.TODO, TaskPriority.HIGH, null, 1L),
                    new Task("T2", "Task 2", null, TaskStatus.TODO, TaskPriority.LOW, null, 2L)
            );
            when(taskRepository.findAll()).thenReturn(tasks);

            List<Task> result = taskService.getAllTasks();

            assertEquals(2, result.size());
            verify(taskRepository).findAll();
        }

        @Test
        @DisplayName("USERは自分のタスクのみ取得")
        void shouldReturnOwnTasksForUser() {
            setSecurityContext(2L, "user1", UserRole.USER);
            List<Task> ownTasks = List.of(
                    new Task("T1", "My Task", null, TaskStatus.TODO, TaskPriority.HIGH, null, 2L)
            );
            when(taskRepository.findByAssignedUserId(2L)).thenReturn(ownTasks);

            List<Task> result = taskService.getAllTasks();

            assertEquals(1, result.size());
            assertEquals("My Task", result.get(0).getTitle());
            verify(taskRepository).findByAssignedUserId(2L);
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

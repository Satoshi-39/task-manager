package com.example.taskmanager.repository;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;
import com.github.database.rider.core.api.configuration.DBUnit;
import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.core.api.dataset.ExpectedDataSet;
import com.github.database.rider.spring.api.DBRider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskRepository のDBUnitテスト。
 *
 * Database Rider（DBUnit ラッパー）を使用し、
 * YAMLデータセットでテストデータを投入・検証する。
 *
 * - @DBRider: JUnit 5 拡張を有効化
 * - @DataSet: テスト前にデータセットをDBに投入
 * - @ExpectedDataSet: テスト後のDB状態を検証
 */
@DataJpaTest
@DBRider
@DBUnit(cacheConnection = false, leakHunter = true, schema = "PUBLIC")
class TaskRepositoryDbUnitTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    @DisplayName("データセットから全件取得できる")
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    void shouldFindAllTasks() {
        List<Task> tasks = taskRepository.findAll();
        assertEquals(3, tasks.size());
    }

    @Test
    @DisplayName("ステータスで検索できる")
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    void shouldFindByStatus() {
        List<Task> todoTasks = taskRepository.findByStatus(TaskStatus.TODO);
        assertEquals(1, todoTasks.size());
        assertEquals("テスト用タスク1", todoTasks.get(0).getTitle());
    }

    @Test
    @DisplayName("優先度で検索できる")
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    void shouldFindByPriority() {
        List<Task> highTasks = taskRepository.findByPriority(TaskPriority.HIGH);
        assertEquals(1, highTasks.size());
        assertEquals("TASK-20260101-0001", highTasks.get(0).getTaskNumber());
    }

    @Test
    @DisplayName("タスク番号で検索できる")
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    void shouldFindByTaskNumber() {
        Optional<Task> result = taskRepository.findByTaskNumber("TASK-20260101-0002");
        assertTrue(result.isPresent());
        assertEquals("テスト用タスク2", result.get().getTitle());
    }

    @Test
    @DisplayName("期限切れタスクを取得できる")
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    void shouldFindOverdueTasks() {
        // 2026-12-31 より前の未完了タスクを検索
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 0, 0);
        List<Task> overdue = taskRepository.findOverdueTasks(now);

        // id=2 (IN_PROGRESS, due 2026-07-15) が期限切れ
        // id=3 (DONE) は完了済みなので除外
        assertEquals(1, overdue.size());
        assertEquals("テスト用タスク2", overdue.get(0).getTitle());
    }

    @Test
    @DisplayName("ステータス別件数をカウントできる")
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    void shouldCountByStatus() {
        long todoCount = taskRepository.countByStatus(TaskStatus.TODO);
        long doneCount = taskRepository.countByStatus(TaskStatus.DONE);
        long inProgressCount = taskRepository.countByStatus(TaskStatus.IN_PROGRESS);

        assertAll(
                () -> assertEquals(1, todoCount),
                () -> assertEquals(1, doneCount),
                () -> assertEquals(1, inProgressCount)
        );
    }

    @Test
    @DisplayName("空のデータセットから0件取得")
    @DataSet(value = "datasets/tasks-empty.yml", cleanBefore = true)
    void shouldReturnEmptyForEmptyDataset() {
        List<Task> tasks = taskRepository.findAll();
        assertTrue(tasks.isEmpty());
    }

    @Test
    @DisplayName("削除後のDB状態を検証")
    @DataSet(value = "datasets/tasks.yml", cleanBefore = true)
    @ExpectedDataSet(value = "datasets/expected-after-delete.yml", ignoreCols = {"description", "due_date", "assigned_user_id", "created_at", "updated_at", "version"})
    void shouldDeleteTaskAndVerifyDbState() {
        taskRepository.deleteById(1L);
        taskRepository.flush();
    }
}

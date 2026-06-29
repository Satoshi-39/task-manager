package com.example.taskmanager.service;

import com.example.taskmanager.common.TaskFilterBuilder;
import com.example.taskmanager.common.TaskNumberGenerator;
import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;
import com.example.taskmanager.dto.request.TaskCreateRequest;
import com.example.taskmanager.dto.request.TaskSearchCriteria;
import com.example.taskmanager.dto.request.TaskUpdateRequest;
import com.example.taskmanager.exception.InvalidStatusTransitionException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * タスクのコアビジネスロジック。
 *
 * Java Gold トピック:
 * - Stream API（groupingBy, partitioningBy, toMap, reduce）
 * - Optional（map, flatMap, orElseThrow チェーン）
 * - Collections高度利用（EnumMap, Comparator合成）
 * - 関数型インタフェース（Predicate<Task> の動的組み立て）
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskNumberGenerator taskNumberGenerator;
    private final TaskNotificationService notificationService;

    public TaskService(TaskRepository taskRepository,
                       TaskNumberGenerator taskNumberGenerator,
                       TaskNotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskNumberGenerator = taskNumberGenerator;
        this.notificationService = notificationService;
    }

    /**
     * タスク作成。
     */
    @Transactional
    public Task createTask(TaskCreateRequest request) {
        String taskNumber = taskNumberGenerator.generate();

        Task task = new Task(
                taskNumber,
                request.getTitle(),
                request.getDescription(),
                TaskStatus.TODO,
                Optional.ofNullable(request.getPriority()).orElse(TaskPriority.MEDIUM),
                request.getDueDate()
        );

        Task saved = taskRepository.save(task);
        log.info("Task created: {} ({})", saved.getTitle(), saved.getTaskNumber());

        // 非同期通知
        notificationService.notifyTaskCreated(saved);

        return saved;
    }

    /**
     * タスク更新（楽観的ロックを version フィールドで実現）。
     */
    @Transactional
    public Task updateTask(Long id, TaskUpdateRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        // ステータス遷移の検証（ステータスが変更される場合のみ）
        if (task.getStatus() != request.getStatus()
                && !task.getStatus().canTransitionTo(request.getStatus())) {
            throw new InvalidStatusTransitionException(task.getStatus(), request.getStatus());
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setPriority(Optional.ofNullable(request.getPriority()).orElse(task.getPriority()));
        task.setDueDate(request.getDueDate());

        Task updated = taskRepository.save(task);
        log.info("Task updated: {} -> {}", id, request.getStatus());

        // ステータスが完了に変わった場合の通知
        if (request.getStatus() == TaskStatus.DONE) {
            notificationService.notifyTaskCompleted(updated);
        }

        return updated;
    }

    /**
     * タスク削除。
     */
    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        taskRepository.delete(task);
        log.info("Task deleted: {}", id);
    }

    /**
     * ID でタスクを取得。
     * Optional の map → orElseThrow チェーンで学習。
     */
    public Task getTask(Long id) {
        return taskRepository.findById(id)
                .map(task -> {
                    log.debug("Task found: {} ({})", task.getTitle(), task.getTaskNumber());
                    return task;
                })
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    /**
     * 全タスクを取得。
     */
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    /**
     * 検索条件によるフィルタリング。
     * Stream API + Predicate 動的合成で実現。
     */
    public List<Task> searchTasks(TaskSearchCriteria criteria) {
        List<Task> allTasks = taskRepository.findAll();

        Predicate<Task> filter = new TaskFilterBuilder()
                .withStatus(criteria.getStatus())
                .withPriority(criteria.getPriority())
                .withKeyword(criteria.getKeyword())
                .withOverdue(criteria.getOverdue())
                .build();

        return allTasks.stream()
                .filter(filter)
                .sorted(Comparator.comparing(Task::getPriority,
                                Comparator.comparingInt(TaskPriority::getLevel).reversed())
                        .thenComparing(Task::getCreatedAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * ステータスごとにグルーピング。
     * Collectors.groupingBy の学習。
     */
    public Map<TaskStatus, List<Task>> groupByStatus() {
        return taskRepository.findAll().stream()
                .collect(Collectors.groupingBy(Task::getStatus));
    }

    /**
     * 完了/未完了でパーティショニング。
     * Collectors.partitioningBy の学習。
     */
    public Map<Boolean, List<Task>> partitionByCompletion() {
        return taskRepository.findAll().stream()
                .collect(Collectors.partitioningBy(
                        task -> task.getStatus() == TaskStatus.DONE));
    }

    /**
     * タスクIDをキーとするMapを生成。
     * Collectors.toMap の学習。
     */
    public Map<Long, Task> toTaskMap() {
        return taskRepository.findAll().stream()
                .collect(Collectors.toMap(Task::getId, task -> task));
    }

    /**
     * ステータスごとのタスク数集計（EnumMap使用）。
     * EnumMap + computeIfAbsent の学習。
     */
    public EnumMap<TaskStatus, Long> countByStatus() {
        EnumMap<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        // 全ステータスを 0 で初期化
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        // 実データで上書き
        taskRepository.findAll().stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()))
                .forEach(counts::put);
        return counts;
    }

    /**
     * 優先度レベルの合計値を reduce で計算。
     * Stream.reduce の学習。
     */
    public int calculateTotalPriorityScore() {
        return taskRepository.findAll().stream()
                .filter(task -> !task.getStatus().isTerminal())
                .map(task -> task.getPriority().getLevel())
                .reduce(0, Integer::sum);
    }

    /**
     * 期限切れタスクの取得。
     */
    public List<Task> getOverdueTasks() {
        return taskRepository.findOverdueTasks(LocalDateTime.now());
    }
}

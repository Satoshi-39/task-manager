package com.example.taskmanager.service;

import com.example.taskmanager.common.TaskEvent;
import com.example.taskmanager.common.TaskEventPublisher;
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
import com.example.taskmanager.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
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
 * <ul>
 *   <li>Stream API（groupingBy, partitioningBy, toMap, reduce）</li>
 *   <li>Optional（map, flatMap, orElseThrow チェーン）</li>
 *   <li>Collections高度利用（EnumMap, Comparator合成）</li>
 *   <li>関数型インタフェース（Predicate&lt;Task&gt; の動的組み立て）</li>
 *   <li>TaskCacheService（ConcurrentHashMap）によるキャッシュ</li>
 *   <li>TaskEventPublisher（CopyOnWriteArrayList）によるイベント駆動</li>
 *   <li>SecurityUtils によるユーザースコープ制御</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskNumberGenerator taskNumberGenerator;
    private final TaskNotificationService notificationService;
    private final TaskCacheService cacheService;
    private final TaskEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository,
                       TaskNumberGenerator taskNumberGenerator,
                       TaskNotificationService notificationService,
                       TaskCacheService cacheService,
                       TaskEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.taskNumberGenerator = taskNumberGenerator;
        this.notificationService = notificationService;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * タスク作成。ログインユーザーの ID を assignedUserId にセットする。
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

        // ログインユーザーの ID をセット
        SecurityUtils.getCurrentUserId().ifPresent(task::setAssignedUserId);

        Task saved = taskRepository.save(task);
        log.info("Task created: {} ({})", saved.getTitle(), saved.getTaskNumber());

        // キャッシュに格納
        cacheService.put(saved.getId(), saved);

        // イベント発行（登録済みリスナーに通知）
        eventPublisher.publish(new TaskEvent(TaskEvent.EventType.CREATED, saved));

        return saved;
    }

    /**
     * タスク更新（楽観的ロックを version フィールドで実現）。
     * ADMIN は全タスク、USER は自分のタスクのみ更新可。
     */
    @Transactional
    public Task updateTask(Long id, TaskUpdateRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        checkOwnership(task);

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

        // キャッシュを更新
        cacheService.put(updated.getId(), updated);

        // イベント発行（登録済みリスナーに通知）
        eventPublisher.publish(new TaskEvent(TaskEvent.EventType.UPDATED, updated));

        // ステータスが完了に変わった場合の通知（既存の直接呼び出しは維持）
        if (request.getStatus() == TaskStatus.DONE) {
            notificationService.notifyTaskCompleted(updated);
        }

        return updated;
    }

    /**
     * タスク削除。
     * ADMIN は全タスク、USER は自分のタスクのみ削除可。
     */
    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        checkOwnership(task);

        taskRepository.delete(task);
        log.info("Task deleted: {}", id);

        // キャッシュから除去
        cacheService.evict(id);

        // イベント発行（登録済みリスナーに通知）
        eventPublisher.publish(new TaskEvent(TaskEvent.EventType.DELETED, task));
    }

    /**
     * ID でタスクを取得。
     * まずキャッシュを確認し、なければ DB から取得する。
     * ADMIN は全タスク、USER は自分のタスクのみ取得可。
     * Optional の map → orElseThrow チェーンで学習。
     */
    public Task getTask(Long id) {
        // ConcurrentHashMap の computeIfAbsent で DB 前にキャッシュを確認
        Task task = cacheService.getOrLoad(id)
                .map(t -> {
                    log.debug("Task found: {} ({})", t.getTitle(), t.getTaskNumber());
                    return t;
                })
                .orElseThrow(() -> new TaskNotFoundException(id));

        checkOwnership(task);

        return task;
    }

    /**
     * 全タスクを取得。
     * ADMIN は全件、USER は自分のタスクのみ返す。
     */
    public List<Task> getAllTasks() {
        if (SecurityUtils.isAdmin()) {
            return taskRepository.findAll();
        }
        return SecurityUtils.getCurrentUserId()
                .map(taskRepository::findByAssignedUserId)
                .orElse(List.of());
    }

    /**
     * 検索条件によるフィルタリング。
     * ADMIN は全件対象、USER は自分のタスクのみ対象。
     * Stream API + Predicate 動的合成で実現。
     */
    public List<Task> searchTasks(TaskSearchCriteria criteria) {
        List<Task> baseTasks = getAllTasks();

        Predicate<Task> filter = new TaskFilterBuilder()
                .withStatus(criteria.getStatus())
                .withPriority(criteria.getPriority())
                .withKeyword(criteria.getKeyword())
                .withOverdue(criteria.getOverdue())
                .build();

        return baseTasks.stream()
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

    /**
     * タスクの所有権チェック。
     * ADMIN は全タスクにアクセス可。USER は自分のタスクのみ。
     */
    private void checkOwnership(Task task) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        SecurityUtils.getCurrentUserId().ifPresent(userId -> {
            if (!userId.equals(task.getAssignedUserId())) {
                throw new AccessDeniedException("このタスクへのアクセス権がありません");
            }
        });
    }
}

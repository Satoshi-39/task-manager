package com.example.taskmanager.service;

import com.example.taskmanager.dto.response.DashboardResponse;
import com.example.taskmanager.repository.TaskJdbcRepository;
import com.example.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ダッシュボード集計サービス。
 *
 * Java Gold トピック:
 * - CompletableFuture.allOf による複数非同期処理の合流
 * - Duration.between による処理時間計測
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final TaskRepository taskRepository;
    private final TaskJdbcRepository taskJdbcRepository;

    public DashboardService(TaskRepository taskRepository,
                            TaskJdbcRepository taskJdbcRepository) {
        this.taskRepository = taskRepository;
        this.taskJdbcRepository = taskJdbcRepository;
    }

    /**
     * ダッシュボードデータを並列で集計する。
     * CompletableFuture.allOf で全集計の完了を待ち合わせる。
     */
    public DashboardResponse getDashboard() {
        Instant start = Instant.now();

        // 各集計を非同期で実行
        CompletableFuture<Map<String, Long>> statusCountsFuture =
                CompletableFuture.supplyAsync(() -> taskJdbcRepository.countGroupByStatus());

        CompletableFuture<Map<String, Long>> priorityCountsFuture =
                CompletableFuture.supplyAsync(() -> taskJdbcRepository.countGroupByPriority());

        CompletableFuture<Long> totalFuture =
                CompletableFuture.supplyAsync(taskRepository::count);

        CompletableFuture<Long> overdueFuture =
                CompletableFuture.supplyAsync(taskJdbcRepository::countOverdue);

        CompletableFuture<Long> doneCountFuture =
                CompletableFuture.supplyAsync(() ->
                        taskRepository.countByStatus(
                                com.example.taskmanager.domain.enums.TaskStatus.DONE));

        // 全集計の完了を待ち合わせ
        CompletableFuture.allOf(
                statusCountsFuture,
                priorityCountsFuture,
                totalFuture,
                overdueFuture,
                doneCountFuture
        ).join();

        // 結果を取得
        Map<String, Long> statusCounts = statusCountsFuture.join();
        Map<String, Long> priorityCounts = priorityCountsFuture.join();
        long total = totalFuture.join();
        long overdue = overdueFuture.join();
        long doneCount = doneCountFuture.join();

        long completionRate = total > 0 ? (doneCount * 100) / total : 0;

        // 処理時間計測
        Duration elapsed = Duration.between(start, Instant.now());
        log.info("Dashboard aggregation completed in {} ms", elapsed.toMillis());

        return new DashboardResponse(
                statusCounts,
                priorityCounts,
                total,
                overdue,
                completionRate,
                elapsed.toMillis()
        );
    }
}

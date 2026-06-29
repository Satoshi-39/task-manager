package com.example.taskmanager.service;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskStatus;
import com.example.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * バッチ処理サービス。
 *
 * Java Gold トピック:
 * - ExecutorService + Callable で並行処理
 * - ReentrantLock でクリティカルセクション保護
 * - AtomicInteger でスレッドセーフなカウント
 * - CountDownLatch / CyclicBarrier のデモ
 */
@Service
public class TaskBatchService {

    private static final Logger log = LoggerFactory.getLogger(TaskBatchService.class);

    private final TaskRepository taskRepository;

    /** 処理済みカウンタ（スレッドセーフ） */
    private final AtomicInteger processedCount = new AtomicInteger(0);

    /** バッチ処理のクリティカルセクション保護 */
    private final ReentrantLock batchLock = new ReentrantLock();

    public TaskBatchService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 期限切れタスクを一括でキャンセル状態に更新する。
     * ExecutorService + Callable で並行処理。
     */
    @Transactional
    public int processOverdueTasks() {
        batchLock.lock();
        try {
            log.info("Starting overdue task batch processing");
            processedCount.set(0);

            List<Task> overdueTasks = taskRepository.findOverdueTasks(LocalDateTime.now());

            if (overdueTasks.isEmpty()) {
                log.info("No overdue tasks found");
                return 0;
            }

            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(overdueTasks.size(), 4));

            try {
                // Callable<Boolean> のリストを作成
                List<Callable<Boolean>> callables = overdueTasks.stream()
                        .<Callable<Boolean>>map(task -> () -> processOneTask(task))
                        .toList();

                // invokeAll で全タスクを実行
                List<Future<Boolean>> futures = executor.invokeAll(callables);

                // 結果を集計
                for (Future<Boolean> future : futures) {
                    try {
                        if (future.get()) {
                            processedCount.incrementAndGet();
                        }
                    } catch (ExecutionException e) {
                        log.error("Task processing failed", e.getCause());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Batch processing interrupted", e);
            } finally {
                executor.shutdown();
            }

            int total = processedCount.get();
            log.info("Batch processing completed: {} tasks processed", total);
            return total;

        } finally {
            batchLock.unlock();
        }
    }

    /**
     * 個々のタスクを処理する。
     */
    private boolean processOneTask(Task task) {
        log.debug("Processing overdue task: {} on thread {}",
                task.getTaskNumber(), Thread.currentThread().getName());
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.save(task);
        return true;
    }

    /**
     * CountDownLatch のデモ：複数の前処理が全て完了するまで待機してから
     * バッチ処理を開始する。
     */
    public String demonstrateCountDownLatch() throws InterruptedException {
        int preparationSteps = 3;
        CountDownLatch latch = new CountDownLatch(preparationSteps);
        StringBuilder result = new StringBuilder();

        ExecutorService executor = Executors.newFixedThreadPool(preparationSteps);

        try {
            for (int i = 1; i <= preparationSteps; i++) {
                final int step = i;
                executor.submit(() -> {
                    try {
                        Thread.sleep(50); // 前処理のシミュレート
                        log.info("Preparation step {} completed on {}",
                                step, Thread.currentThread().getName());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            result.append("All preparation steps completed. Batch processing can begin.");
            log.info(result.toString());
        } finally {
            executor.shutdown();
        }

        return result.toString();
    }

    /**
     * CyclicBarrier のデモ：全ワーカースレッドがバリアに到達してから
     * 一斉に次のフェーズへ進む。
     */
    public String demonstrateCyclicBarrier() throws InterruptedException {
        int workerCount = 3;
        CyclicBarrier barrier = new CyclicBarrier(workerCount, () ->
                log.info("All workers reached barrier - proceeding to next phase"));

        StringBuilder result = new StringBuilder();
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        try {
            List<Future<String>> futures = new java.util.ArrayList<>();

            for (int i = 1; i <= workerCount; i++) {
                final int workerId = i;
                futures.add(executor.submit(() -> {
                    Thread.sleep(workerId * 30L); // 異なる速度でワーク
                    log.info("Worker {} reached barrier", workerId);
                    barrier.await(5, TimeUnit.SECONDS);
                    log.info("Worker {} passed barrier", workerId);
                    return "Worker " + workerId + " completed";
                }));
            }

            for (Future<String> future : futures) {
                try {
                    result.append(future.get(10, TimeUnit.SECONDS)).append("; ");
                } catch (ExecutionException | TimeoutException e) {
                    log.error("Worker error", e);
                }
            }
        } finally {
            executor.shutdown();
        }

        return result.toString().trim();
    }
}

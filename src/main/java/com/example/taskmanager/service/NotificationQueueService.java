package com.example.taskmanager.service;

import com.example.taskmanager.common.TaskEvent;
import com.example.taskmanager.common.TaskEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * BlockingQueue を使用した通知キューサービス（Producer-Consumer パターン）。
 *
 * <p>タスクイベントを即座に処理せず、キューに入れてバックグラウンドの
 * コンシューマースレッドが順次処理する。通知はログ出力でシミュレートする。</p>
 *
 * Java Gold トピック:
 * <ul>
 *   <li>LinkedBlockingQueue — スレッドセーフなブロッキングキュー</li>
 *   <li>Producer-Consumer パターンの実装</li>
 *   <li>put() / take() のブロッキング操作</li>
 *   <li>{@code @PostConstruct} でコンシューマースレッド起動</li>
 *   <li>{@code @PreDestroy} でグレースフルシャットダウン</li>
 * </ul>
 *
 * <h3>BlockingQueue の動作</h3>
 * <pre>
 * Producer（イベント発行側）:
 *   queue.put(event)  ← キューが満杯なら空きが出るまでブロック
 *
 * Consumer（バックグラウンドスレッド）:
 *   event = queue.take()  ← キューが空なら要素が入るまでブロック
 *
 * Producer-Consumer パターンの利点:
 *   1. Producer は通知処理の完了を待たずに次の処理に進める
 *   2. Consumer は自分のペースで順次処理できる
 *   3. キューがバッファとなり、一時的な負荷のスパイクを吸収する
 * </pre>
 *
 * <h3>LinkedBlockingQueue vs ArrayBlockingQueue</h3>
 * <pre>
 * LinkedBlockingQueue:
 *   - リンクリストベース。デフォルトで容量無制限（Integer.MAX_VALUE）
 *   - 容量を指定することも可能
 *   - Producer と Consumer で別々のロックを使用 → 同時に put/take が可能
 *
 * ArrayBlockingQueue:
 *   - 配列ベース。容量の指定が必須
 *   - 1つのロックを共有 → put/take は排他的
 *   - メモリ効率が良い
 * </pre>
 */
@Service
public class NotificationQueueService implements TaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueueService.class);

    /**
     * 通知キュー。容量100のLinkedBlockingQueueを使用。
     * 容量を制限することで、メモリを無制限に消費することを防ぐ。
     */
    private final LinkedBlockingQueue<TaskEvent> queue = new LinkedBlockingQueue<>(100);

    /** コンシューマースレッド */
    private volatile Thread consumerThread;

    /** シャットダウンフラグ（volatile でスレッド間の可視性を保証） */
    private volatile boolean running = false;

    /**
     * コンシューマースレッドを起動する。
     *
     * <p>{@code @PostConstruct} により、Spring Bean の初期化完了後に自動的に呼び出される。
     * デーモンスレッドとして起動することで、JVM シャットダウン時に自動的に停止する。</p>
     */
    @PostConstruct
    public void startConsumer() {
        running = true;
        consumerThread = new Thread(() -> {
            log.info("[NotificationQueue] Consumer thread started");
            while (running) {
                try {
                    // take() はキューが空の間ブロックする。
                    // poll(timeout) を使うことで定期的に running フラグをチェックし、
                    // シャットダウンに応答できるようにする。
                    TaskEvent event = queue.poll(1, TimeUnit.SECONDS);
                    if (event != null) {
                        processEvent(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("[NotificationQueue] Consumer thread interrupted");
                    break;
                }
            }
            log.info("[NotificationQueue] Consumer thread stopped");
        }, "notification-queue-consumer");

        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    /**
     * コンシューマースレッドを停止する。
     *
     * <p>{@code @PreDestroy} により、Spring Bean の破棄前に自動的に呼び出される。
     * running フラグを false にし、スレッドに割り込んでグレースフルに停止する。</p>
     */
    @PreDestroy
    public void shutdown() {
        log.info("[NotificationQueue] Shutting down consumer thread...");
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    /**
     * イベントをキューに追加する（Producer 側）。
     *
     * <p>{@link LinkedBlockingQueue#offer} を使用し、キューが満杯の場合は
     * ドロップしてログに記録する。{@code put()} はブロッキングだが、
     * イベント発行側をブロックしたくないため {@code offer()} を使用。</p>
     *
     * @param event キューに入れるイベント
     */
    public void enqueue(TaskEvent event) {
        boolean offered = queue.offer(event);
        if (offered) {
            log.debug("[NotificationQueue] Event enqueued: {} (queue size: {})",
                    event.getEventType(), queue.size());
        } else {
            log.warn("[NotificationQueue] Queue full! Event dropped: {}", event);
        }
    }

    /**
     * 現在のキューサイズを返す（学習・デバッグ用）。
     *
     * @return キューに溜まっているイベント数
     */
    public int getQueueSize() {
        return queue.size();
    }

    // --- TaskEventListener 実装 ---

    @Override
    public void onTaskCreated(TaskEvent event) {
        enqueue(event);
    }

    @Override
    public void onTaskUpdated(TaskEvent event) {
        enqueue(event);
    }

    @Override
    public void onTaskDeleted(TaskEvent event) {
        enqueue(event);
    }

    // --- private ---

    /**
     * イベントを処理する（Consumer 側）。
     * 実際の通知処理をログ出力でシミュレートする。
     */
    private void processEvent(TaskEvent event) {
        log.info("[NotificationQueue] Processing queued event: type={}, task={}, queuedAt={}",
                event.getEventType(),
                event.getTask().getTaskNumber(),
                event.getOccurredAt());

        switch (event.getEventType()) {
            case CREATED -> log.info("[NotificationQueue] Queued notification: Task {} created",
                    event.getTask().getTaskNumber());
            case UPDATED -> log.info("[NotificationQueue] Queued notification: Task {} updated",
                    event.getTask().getTaskNumber());
            case DELETED -> log.info("[NotificationQueue] Queued notification: Task {} deleted",
                    event.getTask().getTaskNumber());
        }
    }
}

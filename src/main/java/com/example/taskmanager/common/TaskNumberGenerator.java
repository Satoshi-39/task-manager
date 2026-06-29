package com.example.taskmanager.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * スレッドセーフなタスク番号ジェネレータ。
 *
 * Java Gold トピック:
 * - AtomicLong によるスレッドセーフなインクリメント
 * - ReentrantLock によるクリティカルセクション保護
 * - Date/Time API（LocalDate, DateTimeFormatter）
 */
@Component
public class TaskNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AtomicLong sequence = new AtomicLong(0);
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String currentDate = "";

    /**
     * "TASK-yyyyMMdd-0001" 形式のタスク番号を生成する。
     * 日付が変わるとシーケンスをリセットする。
     */
    public String generate() {
        lock.lock();
        try {
            String today = LocalDate.now().format(DATE_FORMAT);

            // 日付が変わったらシーケンスをリセット
            if (!today.equals(currentDate)) {
                currentDate = today;
                sequence.set(0);
            }

            long seq = sequence.incrementAndGet();
            return String.format("TASK-%s-%04d", today, seq);
        } finally {
            lock.unlock();
        }
    }
}

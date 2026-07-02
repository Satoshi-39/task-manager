package com.example.taskmanager.batch.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link TaskJobExecutionListener} のユニットテスト。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>例外処理 — 例外が発生しないことの検証に assertDoesNotThrow を使用</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TaskJobExecutionListenerTest {

    private TaskJobExecutionListener listener;

    @BeforeEach
    void setUp() {
        listener = new TaskJobExecutionListener();
    }

    @Test
    void beforeJobがジョブ情報をログ出力する() {
        JobExecution execution = createJobExecution(BatchStatus.STARTED);

        assertDoesNotThrow(() -> listener.beforeJob(execution));
    }

    @Test
    void afterJobが正常完了時にステータスと統計情報をログ出力する() {
        JobExecution execution = createJobExecution(BatchStatus.COMPLETED);
        execution.setEndTime(LocalDateTime.now());

        // ステップ情報を追加
        StepExecution stepExecution = new StepExecution("testStep", execution);
        stepExecution.setReadCount(10);
        stepExecution.setWriteCount(10);
        execution.addStepExecutions(java.util.List.of(stepExecution));

        assertDoesNotThrow(() -> listener.afterJob(execution));
    }

    @Test
    void afterJobがFAILED時にエラー情報をログ出力する() {
        JobExecution execution = createJobExecution(BatchStatus.FAILED);
        execution.setEndTime(LocalDateTime.now());
        execution.addFailureException(new RuntimeException("テストエラー"));

        assertDoesNotThrow(() -> listener.afterJob(execution));
    }

    @Test
    void afterJobが開始時刻と終了時刻がnullでもエラーにならない() {
        JobInstance jobInstance = new JobInstance(1L, "testJob");
        JobExecution execution = new JobExecution(jobInstance, null);
        execution.setStatus(BatchStatus.COMPLETED);

        assertDoesNotThrow(() -> listener.afterJob(execution));
    }

    private JobExecution createJobExecution(BatchStatus status) {
        JobInstance jobInstance = new JobInstance(1L, "testJob");
        JobExecution execution = new JobExecution(jobInstance, null);
        execution.setStatus(status);
        execution.setStartTime(LocalDateTime.now());
        return execution;
    }
}

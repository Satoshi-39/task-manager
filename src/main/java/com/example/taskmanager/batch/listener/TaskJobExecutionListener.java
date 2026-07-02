package com.example.taskmanager.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * ジョブ実行のライフサイクルを監視するリスナー。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>関数型インタフェース — {@link JobExecutionListener} の実装</li>
 *   <li>Date/Time API — {@link Duration} による経過時間の計算</li>
 *   <li>例外処理 — ジョブ失敗時のエラー情報ログ出力</li>
 * </ul>
 */
@Component
public class TaskJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(TaskJobExecutionListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("=== ジョブ開始: {} (executionId={}) ===",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();

        // Duration で所要時間を計算
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        Duration duration = (start != null && end != null)
                ? Duration.between(start, end)
                : Duration.ZERO;

        // 各ステップの統計情報を集計
        long totalRead = 0;
        long totalWritten = 0;
        long totalSkipped = 0;
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            totalRead += stepExecution.getReadCount();
            totalWritten += stepExecution.getWriteCount();
            totalSkipped += stepExecution.getSkipCount();
        }

        log.info("=== ジョブ終了: {} | ステータス={} | 所要時間={}ms | 読取={} | 書込={} | スキップ={} ===",
                jobName, status, duration.toMillis(), totalRead, totalWritten, totalSkipped);

        if (status == BatchStatus.FAILED) {
            jobExecution.getAllFailureExceptions().forEach(ex ->
                    log.error("ジョブ失敗原因 [{}]: {}", jobName, ex.getMessage(), ex)
            );
        }
    }
}

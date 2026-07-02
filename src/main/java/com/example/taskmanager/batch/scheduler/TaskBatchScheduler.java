package com.example.taskmanager.batch.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * バッチジョブのスケジュール実行を管理する。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>アノテーション — {@link EnableScheduling}, {@link Scheduled} による定期実行</li>
 *   <li>並行処理 — {@code @Scheduled} はデフォルトでシングルスレッドのスケジューラプール上で動作</li>
 *   <li>Date/Time API — {@link LocalDateTime} によるパラメータ生成</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "task.batch.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class TaskBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskBatchScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job taskExportJob;

    public TaskBatchScheduler(JobLauncher jobLauncher,
                              @Qualifier("taskExportJob") Job taskExportJob) {
        this.jobLauncher = jobLauncher;
        this.taskExportJob = taskExportJob;
    }

    /**
     * 毎日午前2時にエクスポートジョブを自動実行する。
     * 実行時刻をパラメータに付与し、毎回ユニークな {@link JobParameters} を生成する。
     */
    @Scheduled(cron = "${task.batch.export.cron:0 0 2 * * *}")
    public void runScheduledExport() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("executionTime", LocalDateTime.now().toString())
                    .toJobParameters();

            log.info("スケジュールエクスポートジョブを開始します");
            jobLauncher.run(taskExportJob, params);
        } catch (Exception e) {
            log.error("スケジュールエクスポートジョブの実行に失敗しました", e);
        }
    }
}

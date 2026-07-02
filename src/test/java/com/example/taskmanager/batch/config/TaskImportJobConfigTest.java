package com.example.taskmanager.batch.config;

import com.example.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * タスクインポートジョブの統合テスト。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>アノテーション — {@code @SpringBatchTest} によるテスト支援</li>
 *   <li>例外処理 — 不正行のスキップ動作を検証</li>
 * </ul>
 */
@SpringBatchTest
@SpringBootTest
class TaskImportJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("taskImportJob")
    private Job taskImportJob;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void 正常なCSVファイルのインポートが完了しDBにレコードが登録される() throws Exception {
        jobLauncherTestUtils.setJob(taskImportJob);

        long countBefore = taskRepository.count();

        // テスト用CSVファイルのパスをジョブパラメータとして渡す
        String csvPath = getClass().getClassLoader()
                .getResource("batch/test-import.csv")
                .getPath();

        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", csvPath)
                .addString("executionTime", LocalDateTime.now().toString())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // CSV の3行分が新規登録されていること
        long countAfter = taskRepository.count();
        assertEquals(countBefore + 3, countAfter, "CSV の3行分のタスクが追加されること");
    }

    @Test
    void 不正行を含むCSVファイルでスキップが動作する() throws Exception {
        jobLauncherTestUtils.setJob(taskImportJob);

        long countBefore = taskRepository.count();

        String csvPath = getClass().getClassLoader()
                .getResource("batch/test-import-with-errors.csv")
                .getPath();

        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", csvPath)
                .addString("executionTime", LocalDateTime.now().toString())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // 不正行があってもジョブは完了する（スキップポリシー）
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // 正常な2行のみが登録されること
        long countAfter = taskRepository.count();
        assertEquals(countBefore + 2, countAfter,
                "不正行をスキップし、正常な2行のみ追加されること");

        // スキップ件数の検証
        for (StepExecution step : execution.getStepExecutions()) {
            assertEquals(1, step.getSkipCount(), "1件のスキップが発生すること");
        }
    }
}

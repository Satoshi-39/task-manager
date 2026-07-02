package com.example.taskmanager.batch.config;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * タスクエクスポートジョブの統合テスト。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>アノテーション — {@code @SpringBatchTest} による Batch テスト支援</li>
 *   <li>NIO.2 — {@link Files#list(Path)} でエクスポートファイルの存在を検証</li>
 * </ul>
 */
@SpringBatchTest
@SpringBootTest
class TaskExportJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("taskExportJob")
    private Job taskExportJob;

    @Test
    void エクスポートジョブが正常に完了しCSVファイルが生成される() throws Exception {
        // JobLauncherTestUtils にテスト対象ジョブを設定
        jobLauncherTestUtils.setJob(taskExportJob);

        JobParameters params = new JobParametersBuilder()
                .addString("executionTime", LocalDateTime.now().toString())
                .toJobParameters();

        // ジョブ実行
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // ジョブが正常完了したことを検証
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // エクスポートディレクトリに CSV ファイルが生成されたことを検証（NIO.2）
        Path exportDir = Paths.get("export");
        assertTrue(Files.exists(exportDir), "export ディレクトリが存在すること");

        try (Stream<Path> files = Files.list(exportDir)) {
            List<Path> csvFiles = files
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .toList();
            assertFalse(csvFiles.isEmpty(), "CSV ファイルが1つ以上存在すること");

            // 最新の CSV ファイルの内容を検証
            Path latestCsv = csvFiles.stream()
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .findFirst()
                    .orElseThrow();

            List<String> lines = Files.readAllLines(latestCsv);
            // ヘッダー行 + データ行があること
            assertTrue(lines.size() > 1, "ヘッダー + データ行が存在すること");
            // ヘッダー行の内容を検証
            assertEquals("taskNumber,title,description,status,priority,dueDate,createdAt",
                    lines.get(0));
        }

        // ステップの統計情報を検証
        execution.getStepExecutions().forEach(step -> {
            assertTrue(step.getReadCount() > 0, "読み取り件数が 0 より大きいこと");
            assertEquals(step.getReadCount(), step.getWriteCount(),
                    "読み取り件数と書き込み件数が一致すること");
        });
    }
}

package com.example.taskmanager.batch.controller;

import com.example.taskmanager.dto.response.ApiResponse;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * バッチジョブの手動実行・ステータス確認用 REST コントローラ。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>Generics — {@code ApiResponse<Map<String, Object>>} の型パラメータ</li>
 *   <li>NIO.2 — アップロードファイルの一時保存に {@link Files#createTempFile} を使用</li>
 *   <li>例外処理 — ジョブ起動時の例外ハンドリング</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/batch")
public class BatchJobController {

    private final JobLauncher jobLauncher;
    private final Job taskExportJob;
    private final Job taskImportJob;
    private final JobExplorer jobExplorer;

    public BatchJobController(JobLauncher jobLauncher,
                              @Qualifier("taskExportJob") Job taskExportJob,
                              @Qualifier("taskImportJob") Job taskImportJob,
                              JobExplorer jobExplorer) {
        this.jobLauncher = jobLauncher;
        this.taskExportJob = taskExportJob;
        this.taskImportJob = taskImportJob;
        this.jobExplorer = jobExplorer;
    }

    /**
     * エクスポートジョブを手動起動する。
     */
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startExport() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("executionTime", LocalDateTime.now().toString())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(taskExportJob, params);

            Map<String, Object> result = buildExecutionResult(execution);
            return ResponseEntity.ok(ApiResponse.success(result, "エクスポートジョブを開始しました"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("エクスポートジョブの起動に失敗しました: " + e.getMessage()));
        }
    }

    /**
     * CSV ファイルをアップロードしてインポートジョブを起動する。
     * NIO.2 の {@link Files#createTempFile} で一時ファイルに保存後、ジョブパラメータとして渡す。
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startImport(
            @RequestParam("file") MultipartFile file) {
        try {
            // NIO.2: 一時ファイルに保存
            Path tempFile = Files.createTempFile("task-import-", ".csv");
            file.transferTo(tempFile);

            JobParameters params = new JobParametersBuilder()
                    .addString("inputFile", tempFile.toAbsolutePath().toString())
                    .addString("executionTime", LocalDateTime.now().toString())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(taskImportJob, params);

            Map<String, Object> result = buildExecutionResult(execution);
            return ResponseEntity.ok(ApiResponse.success(result, "インポートジョブを開始しました"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("インポートジョブの起動に失敗しました: " + e.getMessage()));
        }
    }

    /**
     * ジョブ実行ステータスを確認する。
     */
    @GetMapping("/jobs/{executionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJobStatus(
            @PathVariable Long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = buildExecutionResult(execution);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Map<String, Object> buildExecutionResult(JobExecution execution) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", execution.getId());
        result.put("jobName", execution.getJobInstance().getJobName());
        result.put("status", execution.getStatus().toString());
        result.put("startTime", execution.getStartTime());
        result.put("endTime", execution.getEndTime());

        // ステップ情報の集計
        execution.getStepExecutions().forEach(step -> {
            Map<String, Object> stepInfo = new LinkedHashMap<>();
            stepInfo.put("readCount", step.getReadCount());
            stepInfo.put("writeCount", step.getWriteCount());
            stepInfo.put("skipCount", step.getSkipCount());
            result.put("step_" + step.getStepName(), stepInfo);
        });

        return result;
    }
}

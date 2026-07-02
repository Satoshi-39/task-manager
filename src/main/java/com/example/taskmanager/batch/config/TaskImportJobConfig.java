package com.example.taskmanager.batch.config;

import com.example.taskmanager.batch.dto.TaskCsvRow;
import com.example.taskmanager.batch.listener.TaskJobExecutionListener;
import com.example.taskmanager.common.TaskNumberGenerator;
import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * タスク CSV インポートジョブの設定。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>Generics — {@code ItemProcessor<TaskCsvRow, Task>} 等の型パラメータ</li>
 *   <li>例外処理 — Skip ポリシーで {@link FlatFileParseException} を最大10件までスキップ</li>
 *   <li>関数型インタフェース — Processor をラムダで実装</li>
 *   <li>Date/Time API — CSV 日時文字列の {@link LocalDateTime} パース</li>
 *   <li>Enum — 文字列から {@link TaskStatus} / {@link TaskPriority} への変換</li>
 * </ul>
 */
@Configuration
public class TaskImportJobConfig {

    private static final DateTimeFormatter CSV_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] CSV_FIELD_NAMES = {
            "taskNumber", "title", "description", "status", "priority", "dueDate", "createdAt"
    };

    private final TaskNumberGenerator taskNumberGenerator;

    public TaskImportJobConfig(TaskNumberGenerator taskNumberGenerator) {
        this.taskNumberGenerator = taskNumberGenerator;
    }

    @Bean
    @StepScope
    public FlatFileItemReader<TaskCsvRow> taskImportReader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        return new FlatFileItemReaderBuilder<TaskCsvRow>()
                .name("taskImportReader")
                .resource(new FileSystemResource(inputFile))
                .linesToSkip(1) // ヘッダー行をスキップ
                .delimited()
                .delimiter(",")
                .names(CSV_FIELD_NAMES)
                .targetType(TaskCsvRow.class)
                .build();
    }

    /**
     * CSV 行 → Entity への変換プロセッサ。
     * 新規タスク番号を {@link TaskNumberGenerator} で発番し、
     * Enum 変換と日時パースを行う。
     */
    @Bean
    public ItemProcessor<TaskCsvRow, Task> taskImportProcessor() {
        return csvRow -> {
            // タイトル必須チェック
            if (csvRow.getTitle() == null || csvRow.getTitle().isBlank()) {
                return null; // null を返すとこの行はスキップされる
            }

            // Enum 変換（不正な値は例外 → Skip ポリシーで処理）
            TaskStatus status = TaskStatus.valueOf(csvRow.getStatus());
            TaskPriority priority = TaskPriority.valueOf(csvRow.getPriority());

            // Date/Time API: 日時文字列のパース
            LocalDateTime dueDate = parseDateTime(csvRow.getDueDate());

            // 新規タスク番号を発番（既存の taskNumber は使わない）
            String newTaskNumber = taskNumberGenerator.generate();

            return new Task(
                    newTaskNumber,
                    csvRow.getTitle(),
                    csvRow.getDescription(),
                    status,
                    priority,
                    dueDate
            );
        };
    }

    @Bean
    public RepositoryItemWriter<Task> taskImportWriter(TaskRepository taskRepository) {
        return new RepositoryItemWriterBuilder<Task>()
                .repository(taskRepository)
                .methodName("save")
                .build();
    }

    @Bean
    public Step taskImportStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               FlatFileItemReader<TaskCsvRow> taskImportReader,
                               ItemProcessor<TaskCsvRow, Task> taskImportProcessor,
                               RepositoryItemWriter<Task> taskImportWriter) {
        return new StepBuilder("taskImportStep", jobRepository)
                .<TaskCsvRow, Task>chunk(10, transactionManager)
                .reader(taskImportReader)
                .processor(taskImportProcessor)
                .writer(taskImportWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skipLimit(10)
                .build();
    }

    @Bean
    public Job taskImportJob(JobRepository jobRepository,
                             Step taskImportStep,
                             TaskJobExecutionListener listener) {
        return new JobBuilder("taskImportJob", jobRepository)
                .listener(listener)
                .start(taskImportStep)
                .build();
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, CSV_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

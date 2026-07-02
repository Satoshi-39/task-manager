package com.example.taskmanager.batch.config;

import com.example.taskmanager.batch.dto.TaskCsvRow;
import com.example.taskmanager.batch.listener.TaskJobExecutionListener;
import com.example.taskmanager.domain.entity.Task;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * タスク CSV エクスポートジョブの設定。
 *
 * <p>Java Gold トピック:</p>
 * <ul>
 *   <li>Generics — {@code ItemProcessor<Task, TaskCsvRow>} 等の型パラメータ</li>
 *   <li>関数型インタフェース / ラムダ — Processor の Bean 定義をラムダで記述</li>
 *   <li>Date/Time API — CSV の日時フォーマット変換に {@link DateTimeFormatter} を使用</li>
 *   <li>NIO.2 — 出力ディレクトリの作成に {@link Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[])} を使用</li>
 * </ul>
 */
@Configuration
public class TaskExportJobConfig {

    private static final DateTimeFormatter CSV_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] CSV_HEADERS = {
            "taskNumber", "title", "description", "status", "priority", "dueDate", "createdAt"
    };

    @Value("${task.batch.export.dir:export}")
    private String exportDir;

    @Bean
    public JpaPagingItemReader<Task> taskExportReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<Task>()
                .name("taskExportReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT t FROM Task t ORDER BY t.id")
                .pageSize(10)
                .build();
    }

    /**
     * Entity → CSV 行への変換プロセッサ。
     * ラムダ式で {@link ItemProcessor} 関数型インタフェースを実装する。
     */
    @Bean
    public ItemProcessor<Task, TaskCsvRow> taskExportProcessor() {
        return task -> {
            String dueDate = task.getDueDate() != null
                    ? task.getDueDate().format(CSV_DATE_FORMAT) : "";
            String createdAt = task.getCreatedAt() != null
                    ? task.getCreatedAt().format(CSV_DATE_FORMAT) : "";

            return new TaskCsvRow(
                    task.getTaskNumber(),
                    task.getTitle(),
                    task.getDescription() != null ? task.getDescription() : "",
                    task.getStatus().name(),
                    task.getPriority().name(),
                    dueDate,
                    createdAt
            );
        };
    }

    @Bean
    public FlatFileItemWriter<TaskCsvRow> taskExportWriter() throws Exception {
        // NIO.2: 出力ディレクトリを作成
        Path outputDir = Paths.get(exportDir);
        Files.createDirectories(outputDir);

        String fileName = "tasks_" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        Path outputPath = outputDir.resolve(fileName);

        return new FlatFileItemWriterBuilder<TaskCsvRow>()
                .name("taskExportWriter")
                .resource(new FileSystemResource(outputPath))
                .delimited()
                .delimiter(",")
                .names(CSV_HEADERS)
                .headerCallback(writer -> writer.write(String.join(",", CSV_HEADERS)))
                .build();
    }

    @Bean
    public Step taskExportStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               JpaPagingItemReader<Task> taskExportReader,
                               ItemProcessor<Task, TaskCsvRow> taskExportProcessor,
                               FlatFileItemWriter<TaskCsvRow> taskExportWriter) {
        return new StepBuilder("taskExportStep", jobRepository)
                .<Task, TaskCsvRow>chunk(10, transactionManager)
                .reader(taskExportReader)
                .processor(taskExportProcessor)
                .writer(taskExportWriter)
                .build();
    }

    @Bean
    public Job taskExportJob(JobRepository jobRepository,
                             Step taskExportStep,
                             TaskJobExecutionListener listener) {
        return new JobBuilder("taskExportJob", jobRepository)
                .listener(listener)
                .start(taskExportStep)
                .build();
    }
}

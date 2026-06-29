package com.example.taskmanager.service;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.exception.TaskExportException;
import com.example.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * タスクのCSVエクスポートサービス。
 *
 * Java Gold トピック:
 * - NIO.2（Path, Files.newBufferedWriter, Files.createDirectories）
 * - Date/Time API（DateTimeFormatter でフォーマット）
 * - try-with-resources（例外処理）
 */
@Service
public class TaskExportService {

    private static final Logger log = LoggerFactory.getLogger(TaskExportService.class);

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final String CSV_HEADER = "TaskNumber,Title,Status,Priority,DueDate,CreatedAt";

    private final TaskRepository taskRepository;

    public TaskExportService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * タスク一覧をCSV文字列として出力する（ダウンロード用）。
     */
    public String exportToCsvString() {
        List<Task> tasks = taskRepository.findAll();

        StringWriter sw = new StringWriter();
        sw.write(CSV_HEADER);
        sw.write(System.lineSeparator());

        for (Task task : tasks) {
            sw.write(formatCsvLine(task));
            sw.write(System.lineSeparator());
        }

        log.info("Exported {} tasks to CSV string", tasks.size());
        return sw.toString();
    }

    /**
     * タスク一覧をファイルに出力する。
     * NIO.2 の Path / Files / BufferedWriter を活用。
     */
    public Path exportToFile(Path outputDir) {
        String fileName = "tasks_" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".csv";

        try {
            // ディレクトリが存在しなければ作成
            Files.createDirectories(outputDir);
            Path filePath = outputDir.resolve(fileName);

            List<Task> tasks = taskRepository.findAll();

            // try-with-resources で BufferedWriter を自動クローズ
            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                writer.write(CSV_HEADER);
                writer.newLine();

                for (Task task : tasks) {
                    writer.write(formatCsvLine(task));
                    writer.newLine();
                }
            }

            log.info("Exported {} tasks to file: {}", tasks.size(), filePath.toAbsolutePath());
            return filePath;

        } catch (IOException e) {
            throw new TaskExportException("Failed to export tasks to CSV", e);
        }
    }

    private String formatCsvLine(Task task) {
        return String.join(",",
                escapeCsv(task.getTaskNumber()),
                escapeCsv(task.getTitle()),
                task.getStatus().name(),
                task.getPriority().name(),
                task.getDueDate() != null ? task.getDueDate().format(DISPLAY_DATE_FORMAT) : "",
                task.getCreatedAt().format(DISPLAY_DATE_FORMAT)
        );
    }

    /**
     * CSV用のエスケープ処理。
     * カンマやダブルクオートを含む値をダブルクオートで囲む。
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

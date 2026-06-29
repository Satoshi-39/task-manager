package com.example.taskmanager.controller;

import com.example.taskmanager.service.TaskExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/tasks/export")
public class TaskExportController {

    private final TaskExportService taskExportService;

    public TaskExportController(TaskExportService taskExportService) {
        this.taskExportService = taskExportService;
    }

    /**
     * タスク一覧をCSVファイルとしてダウンロードする。
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv() {
        String csv = taskExportService.exportToCsvString();
        String fileName = "tasks_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                ".csv";

        // BOMを付けてExcelでの文字化けを防ぐ
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(csvBytes, 0, content, bom.length, csvBytes.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content);
    }
}

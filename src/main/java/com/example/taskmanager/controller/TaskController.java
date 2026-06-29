package com.example.taskmanager.controller;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.dto.request.TaskCreateRequest;
import com.example.taskmanager.dto.request.TaskSearchCriteria;
import com.example.taskmanager.dto.request.TaskUpdateRequest;
import com.example.taskmanager.dto.response.ApiResponse;
import com.example.taskmanager.dto.response.TaskResponse;
import com.example.taskmanager.service.TaskBatchService;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * タスクCRUD RESTコントローラ。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskBatchService taskBatchService;

    public TaskController(TaskService taskService, TaskBatchService taskBatchService) {
        this.taskService = taskService;
        this.taskBatchService = taskBatchService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody TaskCreateRequest request) {
        Task task = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(TaskResponse.from(task)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable Long id) {
        Task task = taskService.getTask(id);
        return ResponseEntity.ok(ApiResponse.success(TaskResponse.from(task)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getAllTasks() {
        List<TaskResponse> responses = taskService.getAllTasks().stream()
                .map(TaskResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskUpdateRequest request) {
        Task task = taskService.updateTask(id, request);
        return ResponseEntity.ok(ApiResponse.success(TaskResponse.from(task)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Task deleted successfully"));
    }

    /**
     * 検索エンドポイント。
     * クエリパラメータで検索条件を受け取る。
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> searchTasks(
            @ModelAttribute TaskSearchCriteria criteria) {
        List<TaskResponse> responses = taskService.searchTasks(criteria).stream()
                .map(TaskResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * 期限切れタスクを一括処理する。
     */
    @PostMapping("/batch/process-overdue")
    public ResponseEntity<ApiResponse<String>> processOverdueTasks() {
        int count = taskBatchService.processOverdueTasks();
        return ResponseEntity.ok(ApiResponse.success(count + " overdue tasks processed"));
    }

    /**
     * CountDownLatch デモ。
     */
    @PostMapping("/batch/demo/countdown-latch")
    public ResponseEntity<ApiResponse<String>> demoCountDownLatch() throws InterruptedException {
        String result = taskBatchService.demonstrateCountDownLatch();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * CyclicBarrier デモ。
     */
    @PostMapping("/batch/demo/cyclic-barrier")
    public ResponseEntity<ApiResponse<String>> demoCyclicBarrier() throws InterruptedException {
        String result = taskBatchService.demonstrateCyclicBarrier();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

package com.example.taskmanager.controller;

import com.example.taskmanager.config.SecurityConfig;
import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.domain.enums.TaskPriority;
import com.example.taskmanager.domain.enums.TaskStatus;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.security.CustomUserDetailsService;
import com.example.taskmanager.service.TaskBatchService;
import com.example.taskmanager.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TaskController の MVC テスト。
 *
 * Java Gold トピック:
 * - @WebMvcTest + MockMvc によるコントローラテスト
 * - @WithMockUser による認証済みユーザーのシミュレーション
 * - JSONレスポンスの検証（jsonPath）
 */
@WebMvcTest(TaskController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "admin", roles = "ADMIN")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private TaskBatchService taskBatchService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/tasks - 全タスク取得")
    void shouldGetAllTasks() throws Exception {
        Task task = new Task("TASK-001", "Test", "Desc",
                TaskStatus.TODO, TaskPriority.MEDIUM, null);
        when(taskService.getAllTasks()).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("Test"));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} - 存在しないIDで404")
    void shouldReturn404WhenNotFound() throws Exception {
        when(taskService.getTask(999L)).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/tasks - タイトル未入力で400")
    void shouldReturn400WhenTitleBlank() throws Exception {
        String body = """
                {
                    "title": "",
                    "priority": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /api/tasks - 正常作成で201")
    void shouldCreateTask() throws Exception {
        Task task = new Task("TASK-001", "New Task", "Description",
                TaskStatus.TODO, TaskPriority.HIGH, null);
        when(taskService.createTask(any())).thenReturn(task);

        String body = """
                {
                    "title": "New Task",
                    "description": "Description",
                    "priority": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("New Task"));
    }
}

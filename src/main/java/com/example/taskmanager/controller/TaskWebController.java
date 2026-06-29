package com.example.taskmanager.controller;

import com.example.taskmanager.domain.entity.Task;
import com.example.taskmanager.dto.request.TaskCreateRequest;
import com.example.taskmanager.dto.request.TaskSearchCriteria;
import com.example.taskmanager.dto.request.TaskUpdateRequest;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * タスクCRUD画面コントローラ。
 * HTML フォームは GET/POST のみ対応のため、更新・削除も POST で処理する。
 */
@Controller
@RequestMapping("/tasks")
public class TaskWebController {

    private final TaskService taskService;

    public TaskWebController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * タスク一覧画面。検索条件付き。
     */
    @GetMapping
    public String list(@ModelAttribute("criteria") TaskSearchCriteria criteria, Model model) {
        List<Task> tasks = taskService.searchTasks(criteria);
        model.addAttribute("tasks", tasks);
        return "task/list";
    }

    /**
     * タスク詳細画面。
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Task task = taskService.getTask(id);
        model.addAttribute("task", task);
        return "task/detail";
    }

    /**
     * 新規作成フォーム表示。
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("taskCreateRequest", new TaskCreateRequest());
        return "task/new";
    }

    /**
     * 新規作成処理。
     * バリデーションエラー時はフォームを再表示する。
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("taskCreateRequest") TaskCreateRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "task/new";
        }
        Task created = taskService.createTask(request);
        redirectAttributes.addFlashAttribute("successMessage", "タスクを作成しました。");
        return "redirect:/tasks/" + created.getId();
    }

    /**
     * 編集フォーム表示。
     * 既存のタスクデータを TaskUpdateRequest にマッピングして表示する。
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Task task = taskService.getTask(id);

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setTitle(task.getTitle());
        request.setDescription(task.getDescription());
        request.setStatus(task.getStatus());
        request.setPriority(task.getPriority());
        request.setDueDate(task.getDueDate());
        request.setVersion(task.getVersion());

        model.addAttribute("taskUpdateRequest", request);
        model.addAttribute("taskId", id);
        model.addAttribute("currentStatus", task.getStatus());
        return "task/edit";
    }

    /**
     * 更新処理。
     * バリデーションエラー時はフォームを再表示する。
     */
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("taskUpdateRequest") TaskUpdateRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("taskId", id);
            Task task = taskService.getTask(id);
            model.addAttribute("currentStatus", task.getStatus());
            return "task/edit";
        }
        taskService.updateTask(id, request);
        redirectAttributes.addFlashAttribute("successMessage", "タスクを更新しました。");
        return "redirect:/tasks/" + id;
    }

    /**
     * 削除処理。
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        taskService.deleteTask(id);
        redirectAttributes.addFlashAttribute("successMessage", "タスクを削除しました。");
        return "redirect:/tasks";
    }
}

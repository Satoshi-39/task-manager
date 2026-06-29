package com.example.taskmanager.controller;

import com.example.taskmanager.dto.response.DashboardResponse;
import com.example.taskmanager.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ダッシュボード画面コントローラ。
 * Thymeleaf によるサーバーサイドレンダリングで集計データを表示する。
 */
@Controller
public class DashboardWebController {

    private final DashboardService dashboardService;

    public DashboardWebController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        DashboardResponse dashboard = dashboardService.getDashboard();
        model.addAttribute("dashboard", dashboard);
        return "dashboard";
    }
}

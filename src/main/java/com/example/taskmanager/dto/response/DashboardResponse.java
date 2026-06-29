package com.example.taskmanager.dto.response;

import java.io.Serializable;
import java.util.Map;

public class DashboardResponse implements Serializable {

    private final Map<String, Long> statusCounts;
    private final Map<String, Long> priorityCounts;
    private final long totalTasks;
    private final long overdueTasks;
    private final long completionRate;
    private final long processingTimeMs;

    public DashboardResponse(Map<String, Long> statusCounts,
                             Map<String, Long> priorityCounts,
                             long totalTasks,
                             long overdueTasks,
                             long completionRate,
                             long processingTimeMs) {
        this.statusCounts = statusCounts;
        this.priorityCounts = priorityCounts;
        this.totalTasks = totalTasks;
        this.overdueTasks = overdueTasks;
        this.completionRate = completionRate;
        this.processingTimeMs = processingTimeMs;
    }

    public Map<String, Long> getStatusCounts() { return statusCounts; }
    public Map<String, Long> getPriorityCounts() { return priorityCounts; }
    public long getTotalTasks() { return totalTasks; }
    public long getOverdueTasks() { return overdueTasks; }
    public long getCompletionRate() { return completionRate; }
    public long getProcessingTimeMs() { return processingTimeMs; }
}

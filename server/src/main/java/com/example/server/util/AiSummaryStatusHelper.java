package com.example.server.util;

import com.example.server.dto.PipelineStatusDto;
import com.example.server.pipeline.PipelineStageRecord;

/**
 * 判断 AI 总结是否进行中 / 是否已僵死（DB 仍为「正在分析」但后台已无活跃任务）。
 */
public final class AiSummaryStatusHelper {

    /** 流水线超过该时间未更新，视为任务已结束或僵死 */
    public static final long STALE_PIPELINE_MS = 15 * 60 * 1000L;

    private AiSummaryStatusHelper() {
    }

    public static boolean looksInProgress(String summary) {
        if (summary == null || summary.isBlank() || summary.startsWith("❌")) {
            return false;
        }
        return summary.contains("[MQ]")
                || summary.contains("正在分析")
                || summary.contains("等待调度");
    }

    public static boolean isAnalyzeActuallyRunning(PipelineStatusDto pipeline) {
        if (pipeline == null) {
            return false;
        }
        Long updatedAt = pipeline.getUpdatedAt();
        if (updatedAt == null || System.currentTimeMillis() - updatedAt > STALE_PIPELINE_MS) {
            return false;
        }
        if (pipeline.getCurrentStage() != null && !pipeline.getCurrentStage().isBlank()) {
            return true;
        }
        if (pipeline.getStages() == null) {
            return false;
        }
        for (PipelineStageRecord stage : pipeline.getStages()) {
            if ("running".equals(stage.getStatus())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStaleInProgress(String summary, PipelineStatusDto pipeline) {
        return looksInProgress(summary) && !isAnalyzeActuallyRunning(pipeline);
    }

    public static String staleFailureMessage() {
        return "❌ 上次分析已中断（可能因服务重启或长视频超时），请重新点击 AI 总结";
    }
}

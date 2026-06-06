package com.example.server.util;

import com.example.server.dto.PipelineStatusDto;
import com.example.server.pipeline.PipelineStageRecord;

/** 判断语音转写是否进行中 / 是否已僵死 */
public final class TranscriptStaleHelper {

    public static final long STALE_PIPELINE_MS = AiSummaryStatusHelper.STALE_PIPELINE_MS;

    private static final String STAGE_AUDIO = "AUDIO_EXTRACT";
    private static final String STAGE_ASR = "TRANSCRIPT_ASR";

    private TranscriptStaleHelper() {
    }

    public static boolean isTranscribeActuallyRunning(PipelineStatusDto pipeline) {
        if (pipeline == null) {
            return false;
        }
        Long updatedAt = pipeline.getUpdatedAt();
        if (updatedAt == null || System.currentTimeMillis() - updatedAt > STALE_PIPELINE_MS) {
            return false;
        }
        if (isTranscribeStage(pipeline.getCurrentStage())) {
            return true;
        }
        if (pipeline.getStages() == null) {
            return false;
        }
        for (PipelineStageRecord stage : pipeline.getStages()) {
            if ("running".equals(stage.getStatus()) && isTranscribeStage(stage.getCode())) {
                return true;
            }
        }
        return false;
    }

    /** Redis 标记转写中，但流水线无活跃转写阶段 → 僵死 */
    public static boolean isStaleTranscribing(boolean inRedis, PipelineStatusDto pipeline) {
        return inRedis && !isTranscribeActuallyRunning(pipeline);
    }

    public static String staleFailureMessage() {
        return "❌ 上次转写已中断（可能因服务重启或任务异常退出），请重新点击「提取文字」";
    }

    private static boolean isTranscribeStage(String code) {
        return STAGE_AUDIO.equals(code) || STAGE_ASR.equals(code);
    }
}

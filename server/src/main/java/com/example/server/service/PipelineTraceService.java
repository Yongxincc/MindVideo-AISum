package com.example.server.service;

import com.example.server.dto.PipelineStatusDto;
import com.example.server.pipeline.PipelineStage;
import com.example.server.pipeline.PipelineStageRecord;
import com.example.server.pipeline.PipelineTraceContext;
import com.example.server.util.AiSummaryStatusHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class PipelineTraceService {

    private static final String KEY_PREFIX = "media:pipeline:";
    private static final long TTL_HOURS = 48;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public void beginTask(Long mediaId, String taskType) {
        if (mediaId == null) return;
        PipelineStatusDto existing = load(mediaId);
        if (existing != null && isTaskActive(existing)) {
            existing.setTaskType(taskType);
            existing.setUpdatedAt(System.currentTimeMillis());
            save(existing);
            log(mediaId, null, "TASK_RESUME", -1, "taskType=" + taskType, null);
            return;
        }
        PipelineStatusDto status = new PipelineStatusDto();
        status.setMediaId(mediaId);
        status.setTaskType(taskType);
        status.setUpdatedAt(System.currentTimeMillis());
        status.setStages(new ArrayList<>());
        save(status);
        log(mediaId, null, "TASK_BEGIN", -1, "taskType=" + taskType, null);
    }

    private boolean isTaskActive(PipelineStatusDto status) {
        if (status == null) {
            return false;
        }
        Long updatedAt = status.getUpdatedAt();
        if (updatedAt == null
                || System.currentTimeMillis() - updatedAt > AiSummaryStatusHelper.STALE_PIPELINE_MS) {
            return false;
        }
        if (status.getCurrentStage() != null && !status.getCurrentStage().isBlank()) {
            return true;
        }
        if (status.getStages() == null) {
            return false;
        }
        for (PipelineStageRecord stage : status.getStages()) {
            if ("running".equals(stage.getStatus())) {
                return true;
            }
        }
        return false;
    }

    public void stageStart(PipelineStage stage, String detail) {
        stageStart(resolveMediaId(), stage, detail);
    }

    public void stageStart(Long mediaId, PipelineStage stage, String detail) {
        if (mediaId == null || stage == null) return;
        long now = System.currentTimeMillis();
        PipelineStatusDto status = loadOrCreate(mediaId);
        status.setCurrentStage(stage.name());
        status.setCurrentStageLabel(stage.getLabel());
        status.setCurrentDetail(detail);
        status.setUpdatedAt(now);

        PipelineStageRecord record = findRunning(status, stage.name());
        if (record == null) {
            record = new PipelineStageRecord();
            record.setCode(stage.name());
            record.setLabel(stage.getLabel());
            record.setStatus("running");
            record.setStartedAt(now);
            record.setDetail(detail);
            status.getStages().add(record);
        } else {
            record.setDetail(detail);
            record.setStartedAt(now);
        }
        save(status);
        log(mediaId, stage, "START", -1, detail, null);
    }

    public void stageProgress(PipelineStage stage, String detail) {
        stageProgress(resolveMediaId(), stage, detail);
    }

    public void stageProgress(Long mediaId, PipelineStage stage, String detail) {
        if (mediaId == null || stage == null) return;
        PipelineStatusDto status = loadOrCreate(mediaId);
        status.setCurrentStage(stage.name());
        status.setCurrentStageLabel(stage.getLabel());
        status.setCurrentDetail(detail);
        status.setUpdatedAt(System.currentTimeMillis());
        PipelineStageRecord record = findRunning(status, stage.name());
        if (record != null) {
            record.setDetail(detail);
        }
        save(status);
        log(mediaId, stage, "PROGRESS", -1, detail, null);
    }

    public void stageEnd(PipelineStage stage, boolean ok, String detail, Map<String, Object> metrics) {
        stageEnd(resolveMediaId(), stage, ok, detail, metrics);
    }

    public void stageEnd(Long mediaId, PipelineStage stage, boolean ok, String detail, Map<String, Object> metrics) {
        if (mediaId == null || stage == null) return;
        long now = System.currentTimeMillis();
        PipelineStatusDto status = loadOrCreate(mediaId);
        PipelineStageRecord record = findRunning(status, stage.name());
        if (record == null) {
            record = new PipelineStageRecord();
            record.setCode(stage.name());
            record.setLabel(stage.getLabel());
            record.setStartedAt(now);
            status.getStages().add(record);
        }
        record.setStatus(ok ? "done" : "failed");
        record.setEndedAt(now);
        if (record.getStartedAt() != null) {
            record.setDurationMs(now - record.getStartedAt());
        }
        if (detail != null) {
            record.setDetail(detail);
        }
        if (metrics != null) {
            record.getMetrics().putAll(metrics);
        }
        status.setUpdatedAt(now);
        status.setCurrentDetail(detail);
        if (!ok) {
            status.setCurrentStage(stage.name());
            status.setCurrentStageLabel(stage.getLabel());
        } else if (stage.name().equals(status.getCurrentStage())) {
            status.setCurrentStage(null);
            status.setCurrentStageLabel(null);
        }
        save(status);
        log(mediaId, stage, ok ? "DONE" : "FAILED", record.getDurationMs() != null ? record.getDurationMs() : -1,
                detail, metrics);
    }

    public <T> T runStage(PipelineStage stage, String detail, Supplier<T> work) {
        return runStage(resolveMediaId(), stage, detail, work);
    }

    public <T> T runStage(Long mediaId, PipelineStage stage, String detail, Supplier<T> work) {
        stageStart(mediaId, stage, detail);
        try {
            T result = work.get();
            stageEnd(mediaId, stage, true, detail, null);
            return result;
        } catch (Exception e) {
            stageEnd(mediaId, stage, false, e.getMessage(), null);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    public void runStageVoid(PipelineStage stage, String detail, Runnable work) {
        runStage(stage, detail, () -> {
            work.run();
            return null;
        });
    }

    public PipelineStatusDto getStatus(Long mediaId) {
        if (mediaId == null) return null;
        PipelineStatusDto status = load(mediaId);
        if (status == null) {
            return null;
        }
        if (status.getStages() != null && !status.getStages().isEmpty()) {
            long minStart = status.getStages().stream()
                    .map(PipelineStageRecord::getStartedAt)
                    .filter(t -> t != null)
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(status.getUpdatedAt() != null ? status.getUpdatedAt() : System.currentTimeMillis());
            status.setTotalElapsedMs(System.currentTimeMillis() - minStart);
        }
        return status;
    }

    public void clear(Long mediaId) {
        if (mediaId != null) {
            redisTemplate.delete(KEY_PREFIX + mediaId);
        }
    }

    private Long resolveMediaId() {
        return PipelineTraceContext.get();
    }

    private PipelineStageRecord findRunning(PipelineStatusDto status, String code) {
        if (status.getStages() == null) return null;
        for (int i = status.getStages().size() - 1; i >= 0; i--) {
            PipelineStageRecord r = status.getStages().get(i);
            if (code.equals(r.getCode()) && "running".equals(r.getStatus())) {
                return r;
            }
        }
        return null;
    }

    private PipelineStatusDto loadOrCreate(Long mediaId) {
        PipelineStatusDto existing = load(mediaId);
        if (existing != null) {
            return existing;
        }
        PipelineStatusDto created = new PipelineStatusDto();
        created.setMediaId(mediaId);
        created.setStages(new ArrayList<>());
        return created;
    }

    private PipelineStatusDto load(Long mediaId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + mediaId);
            if (json == null) return null;
            return objectMapper.readValue(json, PipelineStatusDto.class);
        } catch (Exception e) {
            System.err.println("⚠️ [PIPELINE] 读取状态失败 mediaId=" + mediaId + ": " + e.getMessage());
            return null;
        }
    }

    private void save(PipelineStatusDto status) {
        if (status == null || status.getMediaId() == null) return;
        try {
            status.setUpdatedAt(System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(status);
            redisTemplate.opsForValue().set(KEY_PREFIX + status.getMediaId(), json, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("⚠️ [PIPELINE] 保存状态失败 mediaId=" + status.getMediaId() + ": " + e.getMessage());
        }
    }

    public static Map<String, Object> metrics(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private void log(Long mediaId, PipelineStage stage, String status, long durationMs,
                     String detail, Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("[PIPELINE] mediaId=").append(mediaId != null ? mediaId : "-");
        if (stage != null) {
            sb.append(" stage=").append(stage.getLabel()).append("/").append(stage.name());
        }
        sb.append(" event=").append(status);
        if (durationMs >= 0) {
            sb.append(" durationMs=").append(durationMs);
            if (durationMs > 0) {
                sb.append(" durationSec=").append(String.format("%.1f", durationMs / 1000.0));
            }
        }
        if (detail != null && !detail.isBlank()) {
            sb.append(" detail=\"").append(detail.replace("\"", "'")).append("\"");
        }
        if (metrics != null && !metrics.isEmpty()) {
            sb.append(" metrics=").append(metrics);
        }
        System.out.println(sb);
    }
}

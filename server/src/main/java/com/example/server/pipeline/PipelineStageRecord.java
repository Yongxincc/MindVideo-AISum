package com.example.server.pipeline;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class PipelineStageRecord {
    private String code;
    private String label;
    /** running | done | failed */
    private String status;
    private Long startedAt;
    private Long endedAt;
    private Long durationMs;
    private String detail;
    private Map<String, Object> metrics = new LinkedHashMap<>();
}

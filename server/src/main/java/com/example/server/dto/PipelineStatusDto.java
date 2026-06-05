package com.example.server.dto;

import com.example.server.pipeline.PipelineStageRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PipelineStatusDto {
    private Long mediaId;
    /** transcribe | analyze | upload */
    private String taskType;
    private String currentStage;
    private String currentStageLabel;
    private String currentDetail;
    private Long updatedAt;
    private Long totalElapsedMs;
    private List<PipelineStageRecord> stages = new ArrayList<>();
}

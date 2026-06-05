package com.example.server.pipeline;

public enum PipelineStage {
    VIDEO_DOWNLOAD("视频下载"),
    MINIO_UPLOAD("对象存储上传"),
    AUDIO_EXTRACT("音频提取(FFmpeg)"),
    TRANSCRIPT_ASR("语音转文字(ASR)"),
    RAG_INDEX("RAG 向量索引"),
    RAG_RETRIEVE("RAG 语义检索"),
    AI_SUMMARY("AI 智能总结"),
    MQ_DISPATCH("消息队列投递"),
    MQ_CONSUME("消息队列消费");

    private final String label;

    PipelineStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

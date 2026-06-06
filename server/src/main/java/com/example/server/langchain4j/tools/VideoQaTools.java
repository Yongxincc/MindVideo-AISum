package com.example.server.langchain4j.tools;

import com.example.server.entity.MediaFile;
import com.example.server.langchain4j.MediaQaContext;
import com.example.server.langchain4j.RetrievalContext;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.rag.ScoredChunk;
import com.example.server.service.RagIndexService;
import com.example.server.util.TranscriptStatusHelper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LangChain4j @Tool：供大模型按需检索转写、读取总结与元信息（Function Calling）。
 */
@Component
public class VideoQaTools {

    @Autowired
    private RagIndexService ragIndexService;

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Tool("按关键词检索视频转写中最相关的片段")
    public String searchTranscript(@P("检索关键词或用户问题") String query) {
        Long mediaId = MediaQaContext.requireMediaId();
        try {
            List<ScoredChunk> hits = ragIndexService.retrieve(mediaId, query);
            RetrievalContext.mergeHits(hits);
            if (hits.isEmpty()) {
                return "未找到相关转写片段";
            }
            return hits.stream()
                    .map(c -> "[片段#" + (c.getChunkIndex() + 1)
                            + " 相关度=" + String.format("%.2f", c.getScore()) + "]\n"
                            + truncate(c.getContent(), 500))
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            return "检索失败: " + e.getMessage();
        }
    }

    @Tool("获取该视频的 AI 智能总结")
    public String getAiSummary() {
        Long mediaId = MediaQaContext.requireMediaId();
        MediaFile media = mediaFileMapper.selectById(mediaId);
        if (media == null) {
            return "视频不存在";
        }
        String summary = media.getAiSummary();
        if (summary == null || summary.isBlank() || summary.startsWith("❌")) {
            return "该视频尚未生成 AI 总结，请先完成 AI 分析";
        }
        return summary;
    }

    @Tool("获取视频元信息：显示名称、转写状态、总结状态、上传时间等")
    public String getVideoMeta() {
        Long mediaId = MediaQaContext.requireMediaId();
        MediaFile media = mediaFileMapper.selectById(mediaId);
        if (media == null) {
            return "视频不存在";
        }
        String displayName = media.getDisplayName() != null && !media.getDisplayName().isBlank()
                ? media.getDisplayName()
                : media.getFilename();
        String transcriptStatus = media.getTranscriptStatus() != null ? media.getTranscriptStatus() : "NONE";
        boolean transcriptReady = TranscriptStatusHelper.isReady(media);
        boolean hasSummary = media.getAiSummary() != null
                && !media.getAiSummary().isBlank()
                && !media.getAiSummary().startsWith("❌");
        return "显示名称: " + nullToEmpty(displayName)
                + "\n转写状态: " + transcriptStatus + (transcriptReady ? "（已完成）" : "")
                + "\nAI总结: " + (hasSummary ? "已生成" : "未生成")
                + "\n上传时间: " + (media.getUploadTime() != null ? media.getUploadTime() : "未知")
                + (media.getRagIndexedAt() != null ? "\nRAG索引: 已建立" : "\nRAG索引: 未建立");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}

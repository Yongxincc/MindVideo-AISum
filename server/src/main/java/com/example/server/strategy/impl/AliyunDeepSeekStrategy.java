package com.example.server.strategy.impl;

import com.example.server.service.TranscriptSummarizeService;
import com.example.server.strategy.AiAnalysisStrategy;
import com.example.server.utils.AliyunAsrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component("defaultAiStrategy")
public class AliyunDeepSeekStrategy implements AiAnalysisStrategy {

    @Autowired
    private AliyunAsrUtils aliyunAsrUtils;

    @Autowired
    private TranscriptSummarizeService transcriptSummarizeService;

    @Override
    public String transcribe(String videoPath) {
        return processVideoToText(videoPath);
    }

    @Override
    public String generateSummary(String videoPath) {
        return summarizeTranscript(processVideoToText(videoPath));
    }

    @Override
    public String summarizeTranscript(String transcriptText) {
        return summarizeTranscript(null, transcriptText);
    }

    @Override
    public String summarizeTranscript(Long mediaId, String transcriptText) {
        if (transcriptText == null || transcriptText.isBlank()) {
            return "❌ 无转写文本，请先提取文字";
        }
        if (transcriptText.startsWith("❌") || transcriptText.startsWith("处理异常:")) {
            return transcriptText.startsWith("❌") ? transcriptText : "❌ " + transcriptText;
        }
        return transcriptSummarizeService.summarize(mediaId, transcriptText);
    }

    private String processVideoToText(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) return "❌ 路径为空";

        if (!inputPath.startsWith("http")) {
            File localFile = new File(inputPath);
            if (!localFile.exists()) return "❌ 磁盘找不到文件: " + inputPath;
        }

        try {
            System.out.println("🎵 [AI策略] 正在处理视频源: " + inputPath);
            return aliyunAsrUtils.audioToText(inputPath);
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 处理异常: " + e.getMessage();
        }
    }
}

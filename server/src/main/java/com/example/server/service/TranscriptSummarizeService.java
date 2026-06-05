package com.example.server.service;

import com.example.server.utils.DeepSeekUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 视频 AI 总结：将转写全文一次发给大模型（由 {@link DeepSeekUtils} 按模型上下文上限截断）。
 * 与 RAG 无关；向量索引在总结完成后由 {@link RagIndexService} 建立，仅供问答检索。
 */
@Service
public class TranscriptSummarizeService {

    private static final String USER_PREFIX =
            "请对以下视频提取的文字进行总结，不需要废话，直接列出核心观点：\n";

    @Autowired
    private DeepSeekUtils deepSeekUtils;

    public String summarize(Long mediaId, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return "❌ 无转写文本，请先提取文字";
        }
        System.out.println("🤖 [Summary] mediaId=" + mediaId + " 模式=全文直连 chars=" + transcript.length());
        return deepSeekUtils.analyzeContent("summarize", USER_PREFIX + transcript);
    }
}

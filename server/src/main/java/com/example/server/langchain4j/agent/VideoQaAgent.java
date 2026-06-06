package com.example.server.langchain4j.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices 问答 Agent：ContentRetriever + ChatMemory + @Tool。
 */
public interface VideoQaAgent {

    @SystemMessage("""
            你是视频内容问答助手。请基于系统检索到的转写片段、工具返回的信息以及对话历史回答用户问题。
            要求：
            1. 用自然、直接的语言回答，像正常对话一样；
            2. 若转写或总结中没有相关信息，请如实说明，不要编造；
            3. 需要查具体细节时可调用 searchTranscript；需要整体概览时可调用 getAiSummary 或 getVideoMeta。
            """)
    String answer(@MemoryId Long mediaId, @UserMessage String question);
}

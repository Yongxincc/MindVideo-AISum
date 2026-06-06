package com.example.server.service;

import com.example.server.dto.AskResponseDto;
import com.example.server.dto.CitationDto;
import com.example.server.langchain4j.MediaQaContext;
import com.example.server.langchain4j.RetrievalContext;
import com.example.server.langchain4j.agent.VideoQaAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 LangChain4j 的视频问答：ContentRetriever + ChatMemory + @Tool。
 */
@Service
public class RagQaService {

    @Autowired
    private VideoQaAgent videoQaAgent;

    public AskResponseDto answer(Long mediaId, String question) {
        if (question == null || question.isBlank()) {
            return new AskResponseDto("❌ 问题不能为空", List.of());
        }

        MediaQaContext.setMediaId(mediaId);
        RetrievalContext.clear();
        try {
            String answer = videoQaAgent.answer(mediaId, question.trim());
            List<CitationDto> citations = RetrievalContext.toCitations();
            return new AskResponseDto(answer, citations);
        } finally {
            MediaQaContext.clear();
            RetrievalContext.clear();
        }
    }
}

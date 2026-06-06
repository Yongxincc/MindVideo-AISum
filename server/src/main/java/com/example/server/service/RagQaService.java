package com.example.server.service;

import com.example.server.dto.AskResponseDto;
import com.example.server.dto.CitationDto;
import com.example.server.rag.ScoredChunk;
import com.example.server.utils.DeepSeekUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagQaService {

    @Autowired
    private RagIndexService ragIndexService;

    @Autowired
    private DeepSeekUtils deepSeekUtils;

    public AskResponseDto answer(Long mediaId, String question) throws Exception {
        List<ScoredChunk> chunks = ragIndexService.retrieve(mediaId, question);
        if (chunks.isEmpty()) {
            return new AskResponseDto("❌ 暂无可检索的转写内容，请先完成文字提取。", List.of());
        }

        String context = chunks.stream()
                .map(c -> "[引用#" + (c.getChunkIndex() + 1) + "]\n" + c.getContent())
                .collect(Collectors.joining("\n\n"));

        String prompt = """
                用户问题：
                """ + question + """

                转写片段：
                """ + context;

        String answer = deepSeekUtils.analyzeContent("qa", prompt);

        List<CitationDto> citations = chunks.stream()
                .map(c -> new CitationDto(
                        c.getChunkIndex() + 1,
                        c.getScore(),
                        truncate(c.getContent(), 300)))
                .toList();

        return new AskResponseDto(answer, citations);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

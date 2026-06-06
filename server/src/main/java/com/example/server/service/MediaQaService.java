package com.example.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.dto.CitationDto;
import com.example.server.dto.MediaQaMessageDto;
import com.example.server.entity.MediaQaMessage;
import com.example.server.mapper.MediaQaMessageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class MediaQaService {

    @Value("${rag.qa.history-limit:100}")
    private int historyLimit;

    @Autowired
    private MediaQaMessageMapper messageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    public void saveMessage(Long mediaId, String question, String answer,
                           List<CitationDto> citations, String status) {
        if (mediaId == null || question == null || question.isBlank()) {
            return;
        }
        MediaQaMessage row = new MediaQaMessage();
        row.setMediaId(mediaId);
        row.setQuestion(question.trim());
        row.setAnswer(answer);
        row.setStatus(status != null ? status : "OK");
        try {
            if (citations != null && !citations.isEmpty()) {
                row.setCitationsJson(objectMapper.writeValueAsString(citations));
            }
        } catch (Exception e) {
            System.err.println("⚠️ [QA] citations serialize failed: " + e.getMessage());
        }
        messageMapper.insert(row);
    }

    public List<MediaQaMessageDto> listHistory(Long mediaId) {
        if (mediaId == null) {
            return List.of();
        }
        QueryWrapper<MediaQaMessage> q = new QueryWrapper<>();
        q.eq("media_id", mediaId)
                .orderByAsc("created_at")
                .last("LIMIT " + Math.max(1, historyLimit));
        List<MediaQaMessage> rows = messageMapper.selectList(q);
        return rows.stream().map(this::toDto).toList();
    }

    /** @return true 表示已删除 */
    public boolean deleteMessage(Long mediaId, Long messageId) {
        if (mediaId == null || messageId == null) {
            return false;
        }
        MediaQaMessage row = messageMapper.selectById(messageId);
        if (row == null || !mediaId.equals(row.getMediaId())) {
            return false;
        }
        return messageMapper.deleteById(messageId) > 0;
    }

    public int deleteAllByMediaId(Long mediaId) {
        if (mediaId == null) {
            return 0;
        }
        QueryWrapper<MediaQaMessage> q = new QueryWrapper<>();
        q.eq("media_id", mediaId);
        return messageMapper.delete(q);
    }

    private MediaQaMessageDto toDto(MediaQaMessage row) {
        List<CitationDto> citations = parseCitations(row.getCitationsJson());
        return new MediaQaMessageDto(
                row.getId(),
                row.getQuestion(),
                row.getAnswer(),
                citations,
                row.getStatus(),
                row.getCreatedAt()
        );
    }

    private List<CitationDto> parseCitations(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CitationDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}

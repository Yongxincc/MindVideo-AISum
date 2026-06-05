package com.example.server.service;

import com.example.server.dto.AskResponseDto;
import com.example.server.dto.AskStatusDto;
import com.example.server.dto.CitationDto;
import com.example.server.rag.RagIndexException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class RagAskService {

    private static final String KEY_PREFIX = "media:ask:";
    private static final long TTL_HOURS = 2;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagIndexService ragIndexService;

    @Autowired
    private RagQaService ragQaService;

    @Autowired
    private MediaQaService mediaQaService;

    @Autowired
    @Qualifier("aiTaskExecutor")
    private Executor aiTaskExecutor;

    public AskStatusDto getStatus(Long mediaId) {
        if (mediaId == null) {
            return idle();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(mediaId));
            if (json == null) {
                return idle();
            }
            return objectMapper.readValue(json, AskStatusDto.class);
        } catch (Exception e) {
            return new AskStatusDto("FAILED", null, List.of(), "读取问答状态失败: " + e.getMessage());
        }
    }

    /**
     * 提交异步问答；若已有 RUNNING 任务则拒绝重复提交。
     */
    public AskStatusDto submit(Long mediaId, String question) {
        AskStatusDto current = getStatus(mediaId);
        if ("RUNNING".equals(current.getStatus())) {
            return new AskStatusDto("RUNNING", null, List.of(), "问答任务进行中，请稍候");
        }

        AskStatusDto pending = new AskStatusDto(
                "RUNNING",
                null,
                List.of(),
                "正在检索转写并生成回答…");
        save(mediaId, pending);
        aiTaskExecutor.execute(() -> executeAsk(mediaId, question));
        return pending;
    }

    private void executeAsk(Long mediaId, String question) {
        try {
            ragIndexService.ensureIndexedBlocking(mediaId);
            AskResponseDto response = ragQaService.answer(mediaId, question);
            AskStatusDto done = new AskStatusDto(
                    "DONE",
                    response.getAnswer(),
                    response.getCitations() != null ? response.getCitations() : List.of(),
                    null);
            save(mediaId, done);
            persistHistory(mediaId, question, done.getAnswer(), done.getCitations(), "OK");
        } catch (RagIndexException e) {
            String errAnswer = e.getMessage().startsWith("❌") ? e.getMessage() : "❌ " + e.getMessage();
            save(mediaId, new AskStatusDto("FAILED", errAnswer, List.of(), e.getMessage()));
            persistHistory(mediaId, question, errAnswer, List.of(), "FAILED");
        } catch (Exception e) {
            e.printStackTrace();
            String errAnswer = "❌ 问答失败: " + e.getMessage();
            save(mediaId, new AskStatusDto("FAILED", errAnswer, List.of(), e.getMessage()));
            persistHistory(mediaId, question, errAnswer, List.of(), "FAILED");
        }
    }

    private void persistHistory(Long mediaId, String question, String answer,
                                List<CitationDto> citations, String status) {
        try {
            mediaQaService.saveMessage(mediaId, question, answer, citations, status);
        } catch (Exception e) {
            System.err.println("❌ [RAG-Ask] 历史落库失败 mediaId=" + mediaId + ": " + e.getMessage());
        }
    }

    private void save(Long mediaId, AskStatusDto status) {
        try {
            redisTemplate.opsForValue().set(
                    key(mediaId),
                    objectMapper.writeValueAsString(status),
                    TTL_HOURS,
                    TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("❌ [RAG-Ask] Redis 写入失败 mediaId=" + mediaId + ": " + e.getMessage());
        }
    }

    private static String key(Long mediaId) {
        return KEY_PREFIX + mediaId;
    }

    private static AskStatusDto idle() {
        return new AskStatusDto("IDLE", null, List.of(), null);
    }
}

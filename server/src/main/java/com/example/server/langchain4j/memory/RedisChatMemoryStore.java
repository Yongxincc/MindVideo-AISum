package com.example.server.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * LangChain4j ChatMemoryStore 的 Redis 实现，按 mediaId 隔离多轮对话上下文。
 */
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "chat:memory:";

    @Value("${rag.qa.memory-ttl-hours:168}")
    private long memoryTtlHours;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        if (memoryId == null) {
            return new ArrayList<>();
        }
        String json = redisTemplate.opsForValue().get(key(memoryId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(messagesFromJson(json));
        } catch (Exception e) {
            System.err.println("⚠️ [ChatMemory] 反序列化失败 memoryId=" + memoryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId == null) {
            return;
        }
        try {
            String json = messagesToJson(messages != null ? messages : List.of());
            redisTemplate.opsForValue().set(
                    key(memoryId),
                    json,
                    memoryTtlHours,
                    TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("❌ [ChatMemory] Redis 写入失败 memoryId=" + memoryId + ": " + e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        if (memoryId == null) {
            return;
        }
        redisTemplate.delete(key(memoryId));
    }

    public void clearForMedia(Long mediaId) {
        deleteMessages(mediaId);
    }

    private static String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}

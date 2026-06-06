package com.example.server.config;

import com.example.server.langchain4j.agent.VideoQaAgent;
import com.example.server.langchain4j.memory.RedisChatMemoryStore;
import com.example.server.langchain4j.retriever.MediaContentRetriever;
import com.example.server.langchain4j.tools.VideoQaTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${ai.deepseek.api-key}") String apiKey,
            @Value("${ai.deepseek.base-url}") String baseUrl,
            @Value("${ai.deepseek.model}") String modelName,
            @Value("${ai.deepseek.read-timeout-seconds:900}") int readTimeoutSeconds) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .maxRetries(2)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${ai.deepseek.api-key}") String apiKey,
            @Value("${ai.deepseek.base-url}") String baseUrl,
            @Value("${ai.embedding.model:BAAI/bge-m3}") String modelName) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public VideoQaAgent videoQaAgent(
            ChatLanguageModel chatLanguageModel,
            MediaContentRetriever contentRetriever,
            VideoQaTools videoQaTools,
            RedisChatMemoryStore chatMemoryStore,
            @Value("${rag.qa.memory-max-messages:20}") int memoryMaxMessages) {
        return AiServices.builder(VideoQaAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .contentRetriever(contentRetriever)
                .tools(videoQaTools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(memoryMaxMessages)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .build();
    }
}

package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONException;
import okhttp3.*;
import com.example.server.util.RetryHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class DeepSeekUtils {

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${ai.deepseek.model}")
    private String model;

    /** 单次请求 user 侧最大字符数（含提示词前缀）；超出则截断。DeepSeek 等长上下文模型可调大 */
    @Value("${ai.deepseek.max-input-chars:200000}")
    private int maxInputChars;

    /** 长视频全文总结可能较慢，默认 15 分钟读超时 */
    @Value("${ai.deepseek.read-timeout-seconds:900}")
    private int readTimeoutSeconds;

    private OkHttpClient client;

    @PostConstruct
    void initClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                // 长转写 JSON 请求体较大，写超时与读超时对齐，避免上传阶段先超时
                .writeTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .build();
        System.out.println("🤖 [LLM] OkHttp readTimeout=" + readTimeoutSeconds + "s maxInputChars=" + maxInputChars);
    }

    /**
     * 真·AI 深度思考
     */
    public String analyzeContent(String content) {
        return analyzeContent("llm", content);
    }

    public String analyzeContent(String purpose, String content) {
        final String trimmed = trimForModel(content);
        long t0 = System.currentTimeMillis();
        System.out.println("🤖 [LLM] purpose=" + purpose + " model=" + model + " inputChars=" + trimmed.length());

        try {
            String answer = RetryHelper.executeWithBackoff(
                    3,
                    1000,
                    20000,
                    () -> callChatCompletion(purpose, trimmed),
                    e -> {
                        String msg = e.getMessage() != null ? e.getMessage() : "";
                        if (msg.contains("HTTP 4")) return false;
                        return RetryHelper.isRetryableHttpOrNetwork(e) || msg.contains("HTTP 5");
                    }
            );
            long elapsed = System.currentTimeMillis() - t0;
            if (answer != null && answer.startsWith("❌")) {
                System.err.printf("❌ [LLM] purpose=%s api-error elapsedMs=%d msg=%s%n",
                        purpose, elapsed, answer.length() > 200 ? answer.substring(0, 200) + "..." : answer);
            } else {
                System.out.printf("🤖 [LLM] purpose=%s done elapsedMs=%d outputChars=%d charsPerSec=%.1f%n",
                        purpose, elapsed, answer != null ? answer.length() : 0,
                        elapsed > 0 && answer != null ? answer.length() * 1000.0 / elapsed : 0);
            }
            return answer;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.printf("❌ [LLM] purpose=%s failed elapsedMs=%d err=%s%n",
                    purpose, System.currentTimeMillis() - t0, e.getMessage());
            return "❌ 分析失败: " + e.getMessage();
        }
    }

    private static final String SUMMARY_SYSTEM_PROMPT = """
    # Role
    你是一位拥有认知心理学背景的资深信息架构师。你的专长是从杂乱的语音转录文本中提取高价值信息，并进行逻辑重构。

    # Input Context
    用户将提供一段由视频生成的语音识别（ASR）文本。文本可能包含口语废话、重复、语气词或识别错误。

    # Goals
    请忽略文本中的噪音，对内容进行深度降噪和逻辑精炼，最终输出一份结构清晰、语气专业的分析报告。

    # Constraints
    1. **必须**严格遵守下方的输出格式。
    2. 语气保持客观、理性、犀利。
    3. 如果文本内容过短或无意义，直接输出“无法提取有效信息”。
    4. 禁止输出任何开场白或结束语（如“好的，我来分析...”），直接输出 Markdown 内容。

    # Output Format (Markdown)
    请严格按照以下模块输出：

    ## 核心摘要
    （精简概括视频到底讲了什么，直击本质，全面贴切，但要一针见血地概括视频主旨。）

    ## 深度洞察
    （提取 3-5 个核心观点，每个观点使用三级标题格式，如下所示：）
                   
    ### 1. [这里提炼一个 4-8 字的强观点标题]
    不要复述原话。请用专业的语言解释这个观点背后的逻辑、动因或对观众的启示。分析要犀利，直击本质。
                   
    ### 2. [第二个强观点标题]
    （此处填写对应的深度分析...）
                   
    ### 3. [第三个强观点标题]
    （此处填写对应的深度分析...）(后续标题和分析同理)

    ## 原始内容精选
    > "引用视频中原本的最有价值的一句原话（修正错别字后）"
    > "引用第二句有价值的原话"（如果有，不一定必须精选，后续同理，但原始内容精选最多三个）

    ## 🏷️ 领域标签
    #标签1 #标签2 #标签3
    """;

    private static final String QA_SYSTEM_PROMPT = """
    # Role
    你是视频转写内容的问答助手。用户会提供「用户问题」和若干「转写片段」。

    # Rules
    1. 只根据提供的转写片段作答，不要编造片段中未出现的事实。
    2. 用自然、直接的语言回答问题，像正常对话回复一样——**禁止**使用「核心摘要」「深度洞察」「原始内容精选」「领域标签」等总结报告结构。
    3. 回答使用 Markdown；涉及的具体事实请在句末标注引用编号，如 [引用#1]。
    4. 若片段与问题相关但未直接回答，可基于片段中的论述做合理归纳；简要说明「转写未明确谈及 X，以下根据相关讨论归纳」。
    5. 仅当片段与问题完全无关时，简短说明无法从转写中找到相关信息。
    6. 禁止开场白（如「好的，我来回答…」），直接给出答案正文。
    """;

    private String callChatCompletion(String purpose, String content) throws IOException {
        String url = baseUrl + "/chat/completions";
        String systemPrompt = isQaPurpose(purpose) ? QA_SYSTEM_PROMPT : SUMMARY_SYSTEM_PROMPT;

        // 3. 组装 JSON 参数
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("model", model);
        jsonBody.put("stream", false);

        JSONArray messages = new JSONArray();
        messages.add(JSONObject.of("role", "system", "content", systemPrompt));
        messages.add(JSONObject.of("role", "user", "content", content));
        jsonBody.put("messages", messages);

        // 4. 发送请求
        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                System.err.println("❌ [LLM] model=" + model + " http=" + response.code() + " body=" + errBody);
                if (response.code() >= 500) {
                    throw new IOException("HTTP 5xx: " + response.code() + " " + errBody);
                }
                return "❌ AI 请求失败: " + response.code() + " - " + errBody;
            }

            String resultJson = response.body().string();
            JSONObject jsonObject = JSON.parseObject(resultJson);
            JSONArray choices = jsonObject.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("AI 响应 choices 为空");
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (message == null) {
                return "❌ AI 响应格式异常: message 为空";
            }
            String answer = message.getString("content");
            if (answer == null || answer.isBlank()) {
                return "❌ AI 返回内容为空";
            }
            return answer;
        } catch (JSONException e) {
            throw new IOException("AI 响应解析失败: " + e.getMessage(), e);
        }
    }

    private static boolean isQaPurpose(String purpose) {
        return "qa".equals(purpose) || "rag".equals(purpose);
    }

    private String trimForModel(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxInputChars) {
            return content;
        }
        System.out.println("⚠️ [LLM] 输入已截断 " + content.length() + " -> " + maxInputChars + " chars");
        return content.substring(0, maxInputChars) + "\n\n[... 转写文本过长，已截断后分析 ...]";
    }
}
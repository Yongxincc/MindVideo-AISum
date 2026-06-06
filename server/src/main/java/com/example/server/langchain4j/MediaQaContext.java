package com.example.server.langchain4j;

/**
 * 当前问答请求绑定的 mediaId，供 ContentRetriever / @Tool 在 AiServices 调用链内读取。
 */
public final class MediaQaContext {

    private static final ThreadLocal<Long> MEDIA_ID = new ThreadLocal<>();

    private MediaQaContext() {
    }

    public static void setMediaId(Long mediaId) {
        MEDIA_ID.set(mediaId);
    }

    public static Long getMediaId() {
        return MEDIA_ID.get();
    }

    public static Long requireMediaId() {
        Long id = MEDIA_ID.get();
        if (id == null) {
            throw new IllegalStateException("MediaQaContext 未设置 mediaId");
        }
        return id;
    }

    public static void clear() {
        MEDIA_ID.remove();
    }
}

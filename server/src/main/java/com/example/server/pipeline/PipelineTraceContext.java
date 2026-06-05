package com.example.server.pipeline;

public final class PipelineTraceContext {

    private static final ThreadLocal<Long> MEDIA_ID = new ThreadLocal<>();

    private PipelineTraceContext() {}

    public static void set(Long mediaId) {
        if (mediaId != null) {
            MEDIA_ID.set(mediaId);
        }
    }

    public static Long get() {
        return MEDIA_ID.get();
    }

    public static void clear() {
        MEDIA_ID.remove();
    }
}

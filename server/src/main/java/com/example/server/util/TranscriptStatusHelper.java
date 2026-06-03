package com.example.server.util;

import com.example.server.constant.TranscriptStatus;
import com.example.server.entity.MediaFile;

/** 解析/推断转写状态，兼容历史数据（无 transcript_status 字段时） */
public final class TranscriptStatusHelper {

    private TranscriptStatusHelper() {
    }

    public static String resolve(MediaFile file) {
        if (file == null) {
            return TranscriptStatus.NONE;
        }
        String status = file.getTranscriptStatus();
        if (status != null && !status.isBlank()) {
            return status;
        }
        return inferFromText(file.getTranscriptText());
    }

    public static boolean isReady(MediaFile file) {
        return TranscriptStatus.OK.equals(resolve(file));
    }

    public static boolean isFailed(MediaFile file) {
        return TranscriptStatus.FAILED.equals(resolve(file));
    }

    public static boolean isProcessing(MediaFile file) {
        return TranscriptStatus.PROCESSING.equals(resolve(file));
    }

    public static String inferFromText(String text) {
        if (text == null || text.isBlank()) {
            return TranscriptStatus.NONE;
        }
        if (text.startsWith("❌") || text.startsWith("处理异常:")) {
            return TranscriptStatus.FAILED;
        }
        if (text.contains("正在提取语音") || text.contains("正在识别")) {
            return TranscriptStatus.PROCESSING;
        }
        if (text.length() > 20) {
            return TranscriptStatus.OK;
        }
        return TranscriptStatus.NONE;
    }

    public static void applyResult(MediaFile file, String text) {
        if (text == null || text.isBlank()) {
            file.setTranscriptText("❌ 识别结果为空");
            file.setTranscriptStatus(TranscriptStatus.FAILED);
            return;
        }
        file.setTranscriptText(text);
        if (text.startsWith("❌") || text.startsWith("处理异常:")) {
            if (!text.startsWith("❌")) {
                file.setTranscriptText("❌ " + text);
            }
            file.setTranscriptStatus(TranscriptStatus.FAILED);
        } else {
            file.setTranscriptStatus(TranscriptStatus.OK);
        }
    }
}

package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AliyunAsrUtils {

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    /** 单段 ASR 上限 1 小时，默认每段 55 分钟留余量 */
    @Value("${asr.max-segment-seconds:3300}")
    private int maxSegmentSeconds;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public String audioToText(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "❌ 错误：找不到文件";

        double durationSec = probeDurationSeconds(filePath);
        if (durationSec <= 0) {
            return transcribeSingle(filePath);
        }

        if (durationSec <= maxSegmentSeconds) {
            return transcribeSingle(filePath);
        }

        int segmentCount = (int) Math.ceil(durationSec / maxSegmentSeconds);
        System.out.printf("🎤 [ASR] 音频时长 %.0f 秒，超过 %d 秒限制，将分 %d 段识别%n",
                durationSec, maxSegmentSeconds, segmentCount);

        StringBuilder merged = new StringBuilder();
        List<File> tempSegments = new ArrayList<>();

        try {
            for (int i = 0; i < segmentCount; i++) {
                double startSec = (double) i * maxSegmentSeconds;
                double segDuration = Math.min(maxSegmentSeconds, durationSec - startSec);
                String segmentPath = System.getProperty("java.io.tmpdir") + File.separator
                        + "asr_seg_" + UUID.randomUUID() + ".mp3";

                if (!extractAudioSegment(filePath, segmentPath, startSec, segDuration)) {
                    return "❌ 音频分段失败 (第 " + (i + 1) + "/" + segmentCount + " 段)";
                }

                File segmentFile = new File(segmentPath);
                tempSegments.add(segmentFile);

                System.out.printf("🎤 [ASR] 正在识别第 %d/%d 段 (%s - %s)%n",
                        i + 1, segmentCount,
                        formatTimestamp(startSec),
                        formatTimestamp(startSec + segDuration));

                String segmentText = transcribeSingle(segmentPath);
                if (segmentText.startsWith("❌")) {
                    return segmentText;
                }

                if (!segmentText.isBlank()) {
                    if (merged.length() > 0) merged.append("\n\n");
                    merged.append("--- 第 ").append(i + 1).append(" 段 (")
                            .append(formatTimestamp(startSec))
                            .append(" - ")
                            .append(formatTimestamp(startSec + segDuration))
                            .append(") ---\n")
                            .append(segmentText.trim());
                }
            }

            return merged.length() > 0 ? merged.toString() : "❌ 识别结果为空";
        } finally {
            for (File temp : tempSegments) {
                if (temp.exists()) temp.delete();
            }
        }
    }

    private String transcribeSingle(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "❌ 错误：找不到文件";

        String url = "https://api.siliconflow.cn/v1/audio/transcriptions";
        int maxRetries = 3;
        String lastError = "";

        for (int i = 0; i < maxRetries; i++) {
            try {
                System.out.println("🎤 [ASR] 上传中 (第 " + (i + 1) + " 次尝试)...");

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.getName(),
                                RequestBody.create(file, MediaType.parse("application/octet-stream")))
                        .addFormDataPart("model", "TeleAI/TeleSpeechASR")
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String resultJson = response.body().string();
                        JSONObject jsonObject = JSON.parseObject(resultJson);
                        if (jsonObject.containsKey("text")) {
                            String text = jsonObject.getString("text");
                            if (text != null && !text.isBlank()) {
                                return text;
                            }
                            lastError = "响应 text 为空";
                        } else {
                            lastError = "响应缺少 text 字段";
                        }
                        System.err.println("⚠️ ASR 响应异常 (" + (i + 1) + "/" + maxRetries + "): " + lastError);
                    } else {
                        String errBody = response.body() != null ? response.body().string() : "";
                        lastError = "HTTP " + response.code() + ": " + errBody;
                        System.err.println("⚠️ ASR 失败 (" + (i + 1) + "/" + maxRetries + "): " + lastError);

                        if (response.code() >= 500) {
                            Thread.sleep(2000);
                            continue;
                        }
                        return "❌ 识别失败: " + lastError;
                    }
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                System.err.println("⚠️ 网络异常 (" + (i + 1) + "/" + maxRetries + "): " + lastError);
            }
        }

        return "❌ 最终失败 (重试3次): " + lastError;
    }

    private double probeDurationSeconds(String audioPath) {
        Process process = null;
        try {
            List<String> command = List.of(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    audioPath
            );
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }

            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0 || output == null) {
                return -1;
            }
            return Double.parseDouble(output.trim());
        } catch (Exception e) {
            System.err.println("⚠️ ffprobe 读取时长失败: " + e.getMessage());
            return -1;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean extractAudioSegment(String inputPath, String outputPath,
                                        double startSec, double durationSec) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-y");
            command.add("-ss");
            command.add(String.valueOf(startSec));
            command.add("-i");
            command.add(inputPath);
            command.add("-t");
            command.add(String.valueOf(durationSec));
            command.add("-vn");
            command.add("-acodec");
            command.add("libmp3lame");
            command.add("-q:a");
            command.add("2");
            command.add(outputPath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();

            long waitMinutes = Math.max(15, (long) Math.ceil(durationSec / 60.0) + 5);
            boolean finished = process.waitFor(waitMinutes, TimeUnit.MINUTES);
            return finished && process.exitValue() == 0 && new File(outputPath).exists();
        } catch (Exception e) {
            System.err.println("⚠️ 音频分段异常: " + e.getMessage());
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String formatTimestamp(double seconds) {
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}

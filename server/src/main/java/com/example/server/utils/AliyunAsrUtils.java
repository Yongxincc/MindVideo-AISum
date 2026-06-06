package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.example.server.pipeline.PipelineStage;
import com.example.server.pipeline.PipelineTraceContext;
import com.example.server.service.PipelineTraceService;
import com.example.server.util.RetryHelper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
@Component
public class AliyunAsrUtils {

    private static final String PARTIAL_KEY_PREFIX = "media:transcript:partial:";
    private static final long PARTIAL_TTL_HOURS = 24;

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${asr.max-segment-seconds:3300}")
    private int maxSegmentSeconds;

    @Value("${asr.segment-concurrency:3}")
    private int segmentConcurrency;

    @Autowired
    private PipelineTraceService pipelineTrace;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("asrSegmentExecutor")
    private Executor asrSegmentExecutor;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public String audioToText(String mediaPath) {
        if (mediaPath == null || mediaPath.isEmpty()) return "❌ 路径为空";
        if (!mediaPath.startsWith("http")) {
            File file = new File(mediaPath);
            if (!file.exists()) return "❌ 错误：找不到文件";
        }

        Long mediaId = PipelineTraceContext.get();
        double durationSec = probeDurationSeconds(mediaPath);
        int segmentCount = durationSec <= 0 ? 1
                : (int) Math.ceil(durationSec / maxSegmentSeconds);

        String sourceLabel = mediaPath.startsWith("http") ? "远程URL" : "本地";
        pipelineTrace.stageStart(PipelineStage.TRANSCRIPT_ASR,
                "源=" + sourceLabel + ", 预估时长≈"
                        + (durationSec > 0 ? formatDurationMinutes(durationSec) : "未知")
                        + "分钟, 共 " + segmentCount + " 段, 并行度=" + segmentConcurrency);

        if (segmentCount == 1) {
            return transcribeOneSegment(mediaPath, mediaId, 0, 0, durationSec > 0 ? durationSec : -1, 1);
        }

        System.out.printf("🎤 [ASR] 媒体时长 %.0f 秒，分 %d 段识别（并行度 %d）%n",
                durationSec, segmentCount, segmentConcurrency);

        Map<Integer, String> partialSegments = loadPartialSegments(mediaId);
        if (!partialSegments.isEmpty()) {
            System.out.printf("🎤 [ASR] 续跑：已有 %d 段缓存%n", partialSegments.size());
            pipelineTrace.stageProgress(PipelineStage.TRANSCRIPT_ASR,
                    "续跑：已完成 " + partialSegments.size() + "/" + segmentCount + " 段");
        }

        AtomicInteger completedCount = new AtomicInteger(partialSegments.size());
        Semaphore limit = new Semaphore(Math.max(1, segmentConcurrency));

        List<CompletableFuture<SegmentResult>> futures = new ArrayList<>();
        for (int i = 0; i < segmentCount; i++) {
            final int index = i;
            if (partialSegments.containsKey(index)) {
                continue;
            }
            double startSec = (double) i * maxSegmentSeconds;
            double segDuration = Math.min(maxSegmentSeconds, durationSec - startSec);

            CompletableFuture<SegmentResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    limit.acquire();
                    return processSegment(mediaPath, mediaId, index, startSec, segDuration,
                            segmentCount, completedCount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return SegmentResult.failed(index, startSec, startSec + segDuration,
                            "❌ 分段任务被中断");
                } finally {
                    limit.release();
                }
            }, asrSegmentExecutor);
            futures.add(future);
        }

        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        for (CompletableFuture<SegmentResult> future : futures) {
            SegmentResult result = future.join();
            if (result.error != null) {
                return result.error;
            }
            partialSegments.put(result.index, result.text);
        }

        partialSegments = loadPartialSegments(mediaId);
        if (partialSegments.size() < segmentCount) {
            return "❌ 识别未完成，仅完成 " + partialSegments.size() + "/" + segmentCount + " 段";
        }

        String merged = mergeSegments(partialSegments, segmentCount, durationSec);
        clearPartialSegments(mediaId);
        pipelineTrace.stageEnd(PipelineStage.TRANSCRIPT_ASR, true,
                "全部 " + segmentCount + " 段识别完成",
                PipelineTraceService.metrics("segmentCount", segmentCount, "chars", merged.length()));
        return merged.length() > 0 ? merged : "❌ 识别结果为空";
    }

    public void clearPartialSegments(Long mediaId) {
        if (mediaId != null) {
            redisTemplate.delete(PARTIAL_KEY_PREFIX + mediaId);
        }
    }

    private SegmentResult processSegment(String mediaPath, Long mediaId, int index,
                                         double startSec, double segDuration, int segmentCount,
                                         AtomicInteger completedCount) {
        String segmentPath = System.getProperty("java.io.tmpdir") + File.separator
                + "asr_seg_" + UUID.randomUUID() + ".mp3";
        File segmentFile = new File(segmentPath);

        try {
            pipelineTrace.stageProgress(PipelineStage.AUDIO_EXTRACT,
                    String.format("提取第 %d/%d 段 %s–%s", index + 1, segmentCount,
                            formatTimestamp(startSec), formatTimestamp(startSec + segDuration)));

            if (!extractAudioSegment(mediaPath, segmentPath, startSec, segDuration)) {
                return SegmentResult.failed(index, startSec, startSec + segDuration,
                        "❌ 音频分段失败 (第 " + (index + 1) + "/" + segmentCount + " 段)");
            }

            System.out.printf("🎤 [ASR] 正在识别第 %d/%d 段 (%s - %s)%n",
                    index + 1, segmentCount,
                    formatTimestamp(startSec), formatTimestamp(startSec + segDuration));

            long segStart = System.currentTimeMillis();
            String segmentText = transcribeSingle(segmentPath);
            System.out.printf("🎤 [ASR] 第 %d/%d 段完成 elapsedMs=%d chars=%d%n",
                    index + 1, segmentCount, System.currentTimeMillis() - segStart,
                    segmentText != null && !segmentText.startsWith("❌") ? segmentText.length() : 0);

            if (segmentText.startsWith("❌")) {
                return SegmentResult.failed(index, startSec, startSec + segDuration, segmentText);
            }

            savePartialSegment(mediaId, index, segmentText);
            int done = completedCount.incrementAndGet();
            pipelineTrace.stageProgress(PipelineStage.TRANSCRIPT_ASR,
                    String.format("已完成 %d/%d 段", done, segmentCount));

            return SegmentResult.ok(index, startSec, startSec + segDuration, segmentText);
        } finally {
            if (segmentFile.exists()) {
                segmentFile.delete();
            }
        }
    }

    private String transcribeOneSegment(String mediaPath, Long mediaId, int index,
                                        double startSec, double segDuration, int segmentCount) {
        String segmentPath = System.getProperty("java.io.tmpdir") + File.separator
                + "asr_seg_" + UUID.randomUUID() + ".mp3";
        File segmentFile = new File(segmentPath);

        try {
            Map<Integer, String> partial = loadPartialSegments(mediaId);
            if (partial.containsKey(index)) {
                String cached = partial.get(index);
                clearPartialSegments(mediaId);
                pipelineTrace.stageEnd(PipelineStage.TRANSCRIPT_ASR, true, "单段识别完成（缓存）", null);
                return cached;
            }

            pipelineTrace.stageProgress(PipelineStage.AUDIO_EXTRACT, "提取音频…");

            boolean extracted;
            if (segDuration > 0) {
                extracted = extractAudioSegment(mediaPath, segmentPath, startSec, segDuration);
            } else {
                extracted = extractFullAudio(mediaPath, segmentPath);
            }

            if (!extracted) {
                pipelineTrace.stageEnd(PipelineStage.TRANSCRIPT_ASR, false, "音频提取失败", null);
                return "❌ 音频提取失败";
            }

            pipelineTrace.stageProgress(PipelineStage.TRANSCRIPT_ASR, "识别中…");
            String text = transcribeSingle(segmentPath);

            if (!text.startsWith("❌")) {
                pipelineTrace.stageEnd(PipelineStage.TRANSCRIPT_ASR, true, "单段识别完成",
                        PipelineTraceService.metrics("chars", text.length()));
            } else {
                pipelineTrace.stageEnd(PipelineStage.TRANSCRIPT_ASR, false, text, null);
            }
            return text;
        } finally {
            if (segmentFile.exists()) {
                segmentFile.delete();
            }
        }
    }

    private String mergeSegments(Map<Integer, String> partialSegments, int segmentCount, double durationSec) {
        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < segmentCount; i++) {
            String segmentText = partialSegments.get(i);
            if (segmentText == null || segmentText.isBlank()) {
                continue;
            }
            double startSec = (double) i * maxSegmentSeconds;
            double segDuration = durationSec > 0
                    ? Math.min(maxSegmentSeconds, durationSec - startSec)
                    : maxSegmentSeconds;

            if (merged.length() > 0) merged.append("\n\n");
            merged.append("--- 第 ").append(i + 1).append(" 段 (")
                    .append(formatTimestamp(startSec))
                    .append(" - ")
                    .append(formatTimestamp(startSec + segDuration))
                    .append(") ---\n")
                    .append(segmentText.trim());
        }
        return merged.toString();
    }

    private void savePartialSegment(Long mediaId, int index, String text) {
        if (mediaId == null || text == null) return;
        try {
            Map<Integer, String> partial = loadPartialSegments(mediaId);
            partial.put(index, text);
            String json = JSON.toJSONString(partial);
            redisTemplate.opsForValue().set(PARTIAL_KEY_PREFIX + mediaId, json,
                    PARTIAL_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("⚠️ [ASR] 保存段缓存失败: " + e.getMessage());
        }
    }

    private Map<Integer, String> loadPartialSegments(Long mediaId) {
        if (mediaId == null) return new TreeMap<>();
        try {
            String json = redisTemplate.opsForValue().get(PARTIAL_KEY_PREFIX + mediaId);
            if (json == null || json.isBlank()) return new TreeMap<>();
            Map<String, String> raw = JSON.parseObject(json, new TypeReference<Map<String, String>>() {});
            if (raw == null) return new TreeMap<>();
            TreeMap<Integer, String> result = new TreeMap<>();
            for (Map.Entry<String, String> e : raw.entrySet()) {
                result.put(Integer.parseInt(e.getKey()), e.getValue());
            }
            return result;
        } catch (Exception e) {
            System.err.println("⚠️ [ASR] 读取段缓存失败: " + e.getMessage());
            return new TreeMap<>();
        }
    }

    private String transcribeSingle(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "❌ 错误：找不到文件";

        try {
            return RetryHelper.executeWithBackoff(
                    3,
                    1000,
                    15000,
                    () -> callAsrOnce(file),
                    this::isRetryableAsrError
            );
        } catch (Exception e) {
            return "❌ 最终失败: " + e.getMessage();
        }
    }

    private boolean isRetryableAsrError(Exception e) {
        if (e instanceof IOException io && io.getMessage() != null) {
            String msg = io.getMessage();
            if (msg.contains("HTTP 429")) return true;
            if (msg.contains("HTTP 5")) return true;
            if (msg.contains("HTTP 4")) return false;
        }
        return RetryHelper.isRetryableHttpOrNetwork(e)
                || (e.getMessage() != null && e.getMessage().contains("HTTP 5"));
    }

    private String callAsrOnce(File file) throws Exception {
        String url = "https://api.siliconflow.cn/v1/audio/transcriptions";
        long t0 = System.currentTimeMillis();
        System.out.println("🎤 [ASR] 上传识别: " + file.getName() + " (" + (file.length() / 1024) + " KB)");

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
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                if (response.code() == 429) {
                    throw new IOException("HTTP 429: " + errBody);
                }
                if (response.code() >= 500) {
                    throw new IOException("HTTP 5xx: " + response.code() + " " + errBody);
                }
                throw new IOException("HTTP 4xx: " + response.code() + " " + errBody);
            }
            String resultJson = response.body().string();
            JSONObject jsonObject = JSON.parseObject(resultJson);
            if (jsonObject.containsKey("text")) {
                String text = jsonObject.getString("text");
                if (text != null && !text.isBlank()) {
                    System.out.printf("🎤 [ASR] API 返回 elapsedMs=%d chars=%d%n",
                            System.currentTimeMillis() - t0, text.length());
                    return text;
                }
            }
            throw new IOException("ASR 响应缺少有效 text");
        }
    }

    private double probeDurationSeconds(String mediaPath) {
        Process process = null;
        try {
            List<String> command = List.of(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    mediaPath
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
            command.add("-ac");
            command.add("1");
            command.add("-ar");
            command.add("16000");
            command.add("-acodec");
            command.add("libmp3lame");
            command.add("-q:a");
            command.add("4");
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

    private boolean extractFullAudio(String inputPath, String outputPath) {
        Process process = null;
        try {
            List<String> command = List.of(
                    "ffmpeg", "-y",
                    "-i", inputPath,
                    "-vn",
                    "-ac", "1",
                    "-ar", "16000",
                    "-acodec", "libmp3lame",
                    "-q:a", "4",
                    outputPath
            );
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();

            boolean finished = process.waitFor(60, TimeUnit.MINUTES);
            return finished && process.exitValue() == 0 && new File(outputPath).exists();
        } catch (Exception e) {
            System.err.println("⚠️ 全量音频提取异常: " + e.getMessage());
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private long formatDurationMinutes(double seconds) {
        return Math.max(1, (long) Math.ceil(seconds / 60));
    }

    private String formatTimestamp(double seconds) {
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private record SegmentResult(int index, double startSec, double endSec, String text, String error) {
        static SegmentResult ok(int index, double startSec, double endSec, String text) {
            return new SegmentResult(index, startSec, endSec, text, null);
        }

        static SegmentResult failed(int index, double startSec, double endSec, String error) {
            return new SegmentResult(index, startSec, endSec, null, error);
        }
    }
}

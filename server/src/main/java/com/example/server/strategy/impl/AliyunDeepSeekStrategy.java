package com.example.server.strategy.impl;

import com.example.server.strategy.AiAnalysisStrategy;
import com.example.server.utils.AliyunAsrUtils;
import com.example.server.utils.DeepSeekUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component("defaultAiStrategy")
public class AliyunDeepSeekStrategy implements AiAnalysisStrategy {

    @Autowired
    private AliyunAsrUtils aliyunAsrUtils;

    @Autowired
    private DeepSeekUtils deepSeekUtils;

    @Override
    public String transcribe(String videoPath) {
        return processVideoToText(videoPath);
    }

    @Override
    public String generateSummary(String videoPath) {
        return summarizeTranscript(processVideoToText(videoPath));
    }

    @Override
    public String summarizeTranscript(String transcriptText) {
        if (transcriptText == null || transcriptText.isBlank()) {
            return "❌ 无转写文本，请先提取文字";
        }
        if (transcriptText.startsWith("❌") || transcriptText.startsWith("处理异常:")) {
            return transcriptText.startsWith("❌") ? transcriptText : "❌ " + transcriptText;
        }
        return deepSeekUtils.analyzeContent(
                "请对以下视频提取的文字进行总结，不需要废话，直接列出核心观点：\n" + transcriptText);
    }


    private String processVideoToText(String inputPath) {
        //简单检查
        if (inputPath == null || inputPath.isEmpty()) return "❌ 路径为空";

        //如果是本地路径且不存在，报错；如果是 http 链接，跳过检查直接交给 FFmpeg
        if (!inputPath.startsWith("http")) {
            File localFile = new File(inputPath);
            if (!localFile.exists()) return "❌ 磁盘找不到文件: " + inputPath;
        }

        //准备临时 MP3 路径 (放在系统临时目录下)
        String outputMp3Path = System.getProperty("java.io.tmpdir") + File.separator + "temp_" + UUID.randomUUID() + ".mp3";

        try {
            System.out.println("🎵 [AI策略] 正在处理视频源: " + inputPath);

            // 3. 提取音频 (FFmpeg 原生支持 HTTP URL；失败时自动重试一次)
            boolean success = extractAudio(inputPath, outputMp3Path);
            if (!success) {
                System.out.println("⚠️ [AI策略] 首次 FFmpeg 提取失败，正在重试...");
                success = extractAudio(inputPath, outputMp3Path);
            }
            if (!success) return "❌ FFmpeg 转换失败 (可能是网络超时或文件损坏，可点击「重新提取」重试)";

            // 4. 语音转文字
            String text = aliyunAsrUtils.audioToText(outputMp3Path);
            return text;

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 处理异常: " + e.getMessage();
        } finally {
            // 5. 清理临时文件
            File mp3 = new File(outputMp3Path);
            if (mp3.exists()) mp3.delete();
        }
    }

    // === FFmpeg 工具 ===
    private boolean extractAudio(String inputPath, String outputPath) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-y");
            command.add("-i");
            command.add(inputPath);
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
            // 长视频转码可能较久，按源时长估算等待上限（至少 20 分钟，长片额外加 10 分钟余量）
            long durationMin = probeMediaDurationMinutes(inputPath);
            long waitMinutes = Math.max(20, durationMin / 2 + 10);
            if (durationMin >= 90) {
                waitMinutes = Math.max(waitMinutes, durationMin + 15);
            }
            boolean finished = process.waitFor(waitMinutes, java.util.concurrent.TimeUnit.MINUTES);

            if (finished) {
                return process.exitValue() == 0;
            } else {
                process.destroyForcibly();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private long probeMediaDurationMinutes(String mediaPath) {
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
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }

            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                    || process.exitValue() != 0
                    || output == null) {
                return 15;
            }
            double seconds = Double.parseDouble(output.trim());
            return Math.max(1, (long) Math.ceil(seconds / 60));
        } catch (Exception e) {
            return 15;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
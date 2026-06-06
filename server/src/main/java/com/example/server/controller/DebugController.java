package com.example.server.controller;

import com.example.server.auth.AuthContext;
import com.example.server.auth.MediaAccessService;
import com.example.server.dto.AnalysisTaskMsg;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.dto.PipelineStatusDto;
import com.example.server.pipeline.PipelineStage;
import com.example.server.service.AiService;
import com.example.server.service.ContentDedupService;
import com.example.server.service.PipelineTraceService;
import com.example.server.util.TranscriptStatusHelper;
import com.example.server.strategy.AiAnalysisStrategy;
import com.example.server.utils.AliyunAsrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate; // 【修复】导入 Redis 类
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit; //导入时间单位

@RestController
@RequestMapping("/debug")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class DebugController {

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    @Qualifier("defaultAiStrategy")
    private AiAnalysisStrategy aiAnalysisStrategy;

    @Autowired
    private AiService aiService;


    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Autowired
    private org.redisson.api.RedissonClient redissonClient;

    @Autowired
    private ContentDedupService contentDedupService;

    @Autowired
    private PipelineTraceService pipelineTrace;

    @Autowired
    private MediaAccessService mediaAccessService;

    @Autowired
    private AliyunAsrUtils aliyunAsrUtils;

    /** 查询媒体处理流水线各阶段耗时与当前进度（Redis，供前端轮询） */
    @GetMapping("/pipeline")
    public PipelineStatusDto pipelineStatus(@RequestParam Long id) {
        Long userId = AuthContext.requireUserId();
        mediaAccessService.requireOwnedMedia(id, userId);
        PipelineStatusDto status = pipelineTrace.getStatus(id);
        return status != null ? status : new PipelineStatusDto();
    }

    //AI总结接口(分布式锁 + 限流 + MQ)
    @GetMapping("/ai")
    public String aiAnalyze(@RequestParam Long id,
                            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        Long userId = AuthContext.requireUserId();
        MediaFile fileForLock = mediaAccessService.requireOwnedMedia(id, userId);
        String lockKey = contentDedupService.resolveLockKey(fileForLock, "lock:analyze:" + id);
        org.redisson.api.RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, -1, TimeUnit.SECONDS)) {
                return "⚠️ 任务提交中，请勿重复点击！";
            }

            // 这里演示：全局限制每分钟只能分析 10 次 (防止费用爆炸)
            String limitKey = "limit:ai:global";
            org.redisson.api.RRateLimiter rateLimiter = redissonClient.getRateLimiter(limitKey);
            //初始化：每 1 分钟产生 10 个令牌 (RateType.OVERALL 全局, OVER_CLIENT 是单机)
            rateLimiter.trySetRate(org.redisson.api.RateType.OVERALL, 10, 1, org.redisson.api.RateIntervalUnit.MINUTES);

            //尝试获取 1 个令牌
            if (!rateLimiter.tryAcquire(1)) {
                return "⚠️ 系统繁忙(限流中)，请 1 分钟后再试！";
            }

            //查库校验
            MediaFile file = mediaFileMapper.selectById(id);
            if (file == null) return "文件不存在";
            String existingSummary = file.getAiSummary();
            if (!force && existingSummary != null) {
                boolean failed = existingSummary.contains("❌")
                        || existingSummary.contains("请求失败")
                        || existingSummary.contains("分析失败")
                        || existingSummary.contains("Model disabled");
                if (!failed && aiService.isAnalyzeActuallyRunning(id)) {
                    return "任务已在后台运行，无需重复提交";
                }
            }

            pipelineTrace.beginTask(id, "analyze");
            pipelineTrace.stageStart(id, PipelineStage.MQ_DISPATCH, "投递 video-analysis-topic");

            //更新状态
            file.setAiSummary("[MQ] 已进入消息队列，等待调度...");
            mediaFileMapper.updateById(file);
            String userIdKey = (file.getUserId() == null) ? "anon" : String.valueOf(file.getUserId());
            redisTemplate.delete("media:list:user:" + userIdKey);

            if (force) {
                aliyunAsrUtils.clearPartialSegments(id);
            }

            AnalysisTaskMsg msg = new AnalysisTaskMsg(id, "START_ANALYSIS", force);
            rocketMQTemplate.convertAndSend("video-analysis-topic", msg);
            pipelineTrace.stageEnd(id, PipelineStage.MQ_DISPATCH, true, "已投递 RocketMQ", null);

            return "✅ 任务已投递至 RocketMQ！";

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 提交失败: " + e.getMessage();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    //纯文字提取接口
    @GetMapping("/transcribe")
    public String transcribe(@RequestParam Long id,
                             @RequestParam(value = "force", defaultValue = "false") boolean force) {
        Long userId = AuthContext.requireUserId();
        MediaFile mediaFile = mediaAccessService.requireOwnedMedia(id, userId);
        String lockKey = contentDedupService.resolveLockKey(mediaFile, "lock:transcribe:" + id);
        org.redisson.api.RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, -1, TimeUnit.SECONDS)) {
                return "⚠️ 提取任务提交中，请勿重复点击！";
            }

            if (mediaFile == null) return "❌ 找不到文件记录";

            aiService.repairStaleTranscribingLocks(mediaFile);
            if (!force && aiService.isTranscribingForContent(mediaFile)) {
                return "⚠️ 提取任务已在后台运行（含同内容任务），请勿重复提交";
            }
            if (!force && TranscriptStatusHelper.isReady(mediaFile)) {
                return "✅ 已有完整转写结果，可直接查看；如需重做请点「重新提取」";
            }

            if (!aiService.tryMarkTranscribingByContent(mediaFile)) {
                return "⚠️ 提取任务已在后台运行（含同内容任务），请勿重复提交";
            }

            pipelineTrace.beginTask(id, "transcribe");
            if (mediaFile.getUserId() != null) {
                redisTemplate.delete("media:list:user:" + mediaFile.getUserId());
            }

            aiService.asyncTranscribe(id, force);

            return force
                    ? "✅ 已重新提交提取任务！2 小时视频约需 5–10 分钟，请耐心等待。"
                    : "✅ 提取任务已后台运行！2 小时视频约需 5–10 分钟，请耐心等待。";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 提交失败: " + e.getMessage();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    //下载音频接口
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam Long id) throws IOException {
        Long userId = AuthContext.requireUserId();
        MediaFile mediaFile = mediaAccessService.requireOwnedMedia(id, userId);

        String inputPath = mediaFile.getFilePath();

        if (!inputPath.startsWith("http")) {
            if (!new File(inputPath).exists()) return ResponseEntity.notFound().build();
        }

        String outputMp3Path = System.getProperty("java.io.tmpdir") + File.separator + "download_" + UUID.randomUUID() + ".mp3";
        System.out.println("⬇ 下载请求，正在从源地址转码音频: " + inputPath);

        boolean success = runFfmpeg(inputPath, outputMp3Path);

        if (!success) return ResponseEntity.internalServerError().build();

        File mp3File = new File(outputMp3Path);
        Resource resource = new FileSystemResource(mp3File);

        String fileName = "audio.mp3";
        if (mediaFile.getFilename() != null) {
            fileName = mediaFile.getFilename().replaceAll("\\.[^.]+$", "") + ".mp3";
        }
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    private boolean runFfmpeg(String inputPath, String outputPath) {
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
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            process = pb.start();

            long durationMin = probeMediaDurationMinutes(inputPath);
            long waitMinutes = Math.max(20, durationMin / 2 + 10);
            if (durationMin >= 90) {
                waitMinutes = Math.max(waitMinutes, durationMin + 15);
            }

            boolean finished = process.waitFor(waitMinutes, TimeUnit.MINUTES);
            return finished && process.exitValue() == 0;
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

            if (!process.waitFor(30, TimeUnit.SECONDS)
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
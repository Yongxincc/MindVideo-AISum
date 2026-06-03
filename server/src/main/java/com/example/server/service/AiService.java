package com.example.server.service;

import com.example.server.constant.TranscriptStatus;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.strategy.AiAnalysisStrategy;
import com.example.server.util.TranscriptStatusHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    public static final String TRANSCRIBING_KEY_PREFIX = "media:transcribing:";

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    @Qualifier("defaultAiStrategy")
    private AiAnalysisStrategy aiAnalysisStrategy;

    @Autowired
    private StringRedisTemplate redisTemplate;


    @Async("aiTaskExecutor")
    public void asyncAnalyze(Long mediaId) {
        System.out.println(" [线程池] 开始处理任务，ID: " + mediaId);

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) return;

        try {
            mediaFile.setAiSummary("正在分析中，请稍候...");
            mediaFileMapper.updateById(mediaFile);
            clearListCache(mediaFile);

            String text = mediaFile.getTranscriptText();
            if (!TranscriptStatusHelper.isReady(mediaFile)) {
                if (isTranscribing(mediaId) || TranscriptStatusHelper.isProcessing(mediaFile)) {
                    mediaFile.setAiSummary("⚠️ 文字提取进行中，请稍候完成后再分析");
                    mediaFileMapper.updateById(mediaFile);
                    clearListCache(mediaFile);
                    return;
                }
                text = aiAnalysisStrategy.transcribe(mediaFile.getFilePath());
                TranscriptStatusHelper.applyResult(mediaFile, text);
                mediaFileMapper.updateById(mediaFile);
            }

            if (!TranscriptStatusHelper.isReady(mediaFile)) {
                mediaFile.setAiSummary(normalizeErrorMessage(mediaFile.getTranscriptText()));
                mediaFileMapper.updateById(mediaFile);
                clearListCache(mediaFile);
                return;
            }

            text = mediaFile.getTranscriptText();
            String summary = aiAnalysisStrategy.summarizeTranscript(text);
            mediaFile.setAiSummary(summary);
            mediaFileMapper.updateById(mediaFile);

            clearListCache(mediaFile);
            System.out.println("✅ [线程池] 任务全部完成，前端轮询将在下一次命中新数据。");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ [线程池] 任务失败: " + e.getMessage());
            mediaFile.setAiSummary("❌ 分析失败: " + e.getMessage());
            mediaFileMapper.updateById(mediaFile);
            clearListCache(mediaFile);
        }
    }

    private void clearListCache(MediaFile mediaFile) {
        String userIdStr = (mediaFile.getUserId() == null) ? "anon" : String.valueOf(mediaFile.getUserId());
        String cacheKey = "media:list:user:" + userIdStr;
        Boolean deleteResult = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleteResult)) {
            System.out.println(" [线程池] 缓存清除成功！Key: " + cacheKey);
        }
    }



    @Async("aiTaskExecutor")
    public void asyncTranscribe(Long mediaId) {
        asyncTranscribe(mediaId, false);
    }

    @Async("aiTaskExecutor")
    public void asyncTranscribe(Long mediaId, boolean force) {
        System.out.println(" [线程池] 开始全文提取任务，ID: " + mediaId + (force ? " (强制重试)" : ""));

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) return;

        try {
            if (!force && TranscriptStatusHelper.isReady(mediaFile)) {
                System.out.println(" [线程池] 已有有效转写，跳过重复提取，ID: " + mediaId);
                clearTranscribing(mediaId);
                return;
            }

            String text = aiAnalysisStrategy.transcribe(mediaFile.getFilePath());
            TranscriptStatusHelper.applyResult(mediaFile, text);
            mediaFileMapper.updateById(mediaFile);
            clearTranscribing(mediaId);
            clearListCache(mediaFile);

            System.out.println(" [线程池] 全文提取完成，ID: " + mediaId);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(" [线程池] 提取失败: " + e.getMessage());
            TranscriptStatusHelper.applyResult(mediaFile, "❌ 提取失败: " + e.getMessage());
            mediaFileMapper.updateById(mediaFile);
            clearTranscribing(mediaId);
            clearListCache(mediaFile);
        }
    }

    public void markTranscribing(Long mediaId) {
        redisTemplate.opsForValue().set(
                TRANSCRIBING_KEY_PREFIX + mediaId, "1", 4, java.util.concurrent.TimeUnit.HOURS);

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile != null) {
            mediaFile.setTranscriptStatus(TranscriptStatus.PROCESSING);
            mediaFileMapper.updateById(mediaFile);
        }
    }

    public void clearTranscribing(Long mediaId) {
        redisTemplate.delete(TRANSCRIBING_KEY_PREFIX + mediaId);
    }

    public boolean isTranscribing(Long mediaId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TRANSCRIBING_KEY_PREFIX + mediaId));
    }

    public void enrichTranscribingFlags(java.util.List<MediaFile> list) {
        if (list == null) return;
        for (MediaFile file : list) {
            if (file.getId() != null) {
                boolean inRedis = isTranscribing(file.getId());
                file.setTranscribing(inRedis);
                if (inRedis && !TranscriptStatus.OK.equals(TranscriptStatusHelper.resolve(file))) {
                    file.setTranscriptStatus(TranscriptStatus.PROCESSING);
                }
            }
        }
    }

    /** @deprecated 请使用 {@link TranscriptStatusHelper#isReady(MediaFile)} */
    @Deprecated
    public static boolean isInvalidTranscript(String text) {
        return !TranscriptStatus.OK.equals(TranscriptStatusHelper.inferFromText(text));
    }

    static String normalizeErrorMessage(String text) {
        if (text == null || text.isBlank()) {
            return "❌ 无转写文本，请先提取文字";
        }
        if (text.startsWith("❌")) {
            return text;
        }
        return "❌ " + text;
    }
}

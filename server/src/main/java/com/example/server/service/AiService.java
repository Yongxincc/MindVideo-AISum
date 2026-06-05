package com.example.server.service;



import com.example.server.constant.TranscriptStatus;

import com.example.server.entity.MediaFile;

import com.example.server.mapper.MediaFileMapper;

import com.example.server.strategy.AiAnalysisStrategy;

import com.example.server.pipeline.PipelineStage;

import com.example.server.pipeline.PipelineTraceContext;

import com.example.server.dto.PipelineStatusDto;
import com.example.server.util.AiSummaryStatusHelper;
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



    @Autowired

    private ContentDedupService contentDedupService;



    @Autowired

    private RagIndexService ragIndexService;



    @Autowired

    private PipelineTraceService pipelineTrace;



    @Async("aiTaskExecutor")

    public void asyncAnalyze(Long mediaId) {

        System.out.println(" [线程池] 开始处理任务，ID: " + mediaId);

        pipelineTrace.beginTask(mediaId, "analyze");

        PipelineTraceContext.set(mediaId);



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

                if (!runTranscribePipeline(mediaFile, false)) {

                    // dedup or failure handled inside

                }

                mediaFile = mediaFileMapper.selectById(mediaId);

            }



            if (!TranscriptStatusHelper.isReady(mediaFile)) {

                mediaFile.setAiSummary(normalizeErrorMessage(mediaFile.getTranscriptText()));

                mediaFileMapper.updateById(mediaFile);

                clearListCache(mediaFile);

                return;

            }



            text = mediaFile.getTranscriptText();

            pipelineTrace.stageStart(mediaId, PipelineStage.AI_SUMMARY,

                    "转写字数=" + (text != null ? text.length() : 0));

            long t0 = System.currentTimeMillis();

            String summary = aiAnalysisStrategy.summarizeTranscript(mediaId, text);

            long elapsed = System.currentTimeMillis() - t0;

            pipelineTrace.stageEnd(mediaId, PipelineStage.AI_SUMMARY, !summary.startsWith("❌"),

                    "完成", PipelineTraceService.metrics(

                    "transcriptChars", text != null ? text.length() : 0,

                    "summaryChars", summary != null ? summary.length() : 0,

                    "elapsedMs", elapsed));

            System.out.println("🤖 [线程池] AI 总结完成 mediaId=" + mediaId

                    + " chars=" + (text != null ? text.length() : 0)

                    + " elapsedMs=" + elapsed);

            mediaFile.setAiSummary(summary);

            mediaFileMapper.updateById(mediaFile);

            clearListCache(mediaFile);



            if (!summary.startsWith("❌")) {

                indexTranscriptForQa(mediaId);

            }



            System.out.println("✅ [线程池] 任务全部完成，前端轮询将在下一次命中新数据。");



        } catch (Exception e) {

            e.printStackTrace();

            System.err.println("❌ [线程池] 任务失败: " + e.getMessage());

            pipelineTrace.stageEnd(mediaId, PipelineStage.AI_SUMMARY, false, e.getMessage(), null);

            mediaFile.setAiSummary("❌ 分析失败: " + e.getMessage());

            mediaFileMapper.updateById(mediaFile);

            clearListCache(mediaFile);

        } finally {

            PipelineTraceContext.clear();

        }

    }



    /** 总结完成后为「向视频提问」建立 RAG 向量索引（与总结生成解耦） */

    private void indexTranscriptForQa(Long mediaId) {

        try {

            String indexErr = ragIndexService.indexTranscriptWithError(mediaId);
            if (indexErr == null) {
                System.out.println("📚 [RAG] 总结后已建立问答索引 mediaId=" + mediaId);
            } else {
                System.out.println("⚠️ [RAG] 总结后索引失败 mediaId=" + mediaId + ": " + indexErr);
            }

        } catch (Exception e) {

            System.err.println("⚠️ [RAG] 总结后索引失败 mediaId=" + mediaId + ": " + e.getMessage());

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

        pipelineTrace.beginTask(mediaId, "transcribe");

        PipelineTraceContext.set(mediaId);



        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);

        if (mediaFile == null) return;



        try {

            if (!force && TranscriptStatusHelper.isReady(mediaFile)) {

                System.out.println(" [线程池] 已有有效转写，跳过重复提取，ID: " + mediaId);

                clearTranscribing(mediaId);

                return;

            }



            if (!force && contentDedupService.tryReuseTranscript(mediaFile)) {

                clearTranscribing(mediaId);

                clearListCache(mediaFile);

                System.out.println(" [线程池] MD5 去重复用转写，ID: " + mediaId);

                return;

            }



            if (force) {

                ragIndexService.deleteChunks(mediaId);

            }



            runTranscribePipeline(mediaFile, force);

            clearTranscribing(mediaId);

            clearListCache(mediaFile);



            System.out.println(" [线程池] 全文提取完成，ID: " + mediaId);



        } catch (Exception e) {

            e.printStackTrace();

            System.err.println(" [线程池] 提取失败: " + e.getMessage());

            pipelineTrace.stageEnd(mediaId, PipelineStage.TRANSCRIPT_ASR, false, e.getMessage(), null);

            TranscriptStatusHelper.applyResult(mediaFile, "❌ 提取失败: " + e.getMessage());

            mediaFileMapper.updateById(mediaFile);

            clearTranscribing(mediaId);

            clearListCache(mediaFile);

        } finally {

            PipelineTraceContext.clear();

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

            if (file.getId() == null) continue;

            boolean inRedis = isTranscribing(file.getId());

            file.setTranscribing(inRedis);

            if (inRedis && !TranscriptStatus.OK.equals(TranscriptStatusHelper.resolve(file))) {

                file.setTranscriptStatus(TranscriptStatus.PROCESSING);

                continue;

            }

            repairStaleProcessingStatus(file, inRedis);

            repairStaleAiSummary(file);

        }

    }

    public boolean isAnalyzeActuallyRunning(Long mediaId) {
        PipelineStatusDto pipeline = pipelineTrace.getStatus(mediaId);
        return AiSummaryStatusHelper.isAnalyzeActuallyRunning(pipeline);
    }

    private void repairStaleAiSummary(MediaFile file) {
        if (file.getId() == null || !AiSummaryStatusHelper.looksInProgress(file.getAiSummary())) {
            return;
        }
        PipelineStatusDto pipeline = pipelineTrace.getStatus(file.getId());
        if (!AiSummaryStatusHelper.isStaleInProgress(file.getAiSummary(), pipeline)) {
            return;
        }
        String repaired = AiSummaryStatusHelper.staleFailureMessage();
        System.out.println("⚠️ [总结状态修复] mediaId=" + file.getId()
                + " 僵死进行中 -> 可重新提交");
        file.setAiSummary(repaired);
        mediaFileMapper.updateById(file);
        clearListCache(file);
    }



    /**

     * 服务重启或任务异常退出后，DB 可能仍为 PROCESSING 但 Redis 标记已消失，需自动修复。

     */

    private void repairStaleProcessingStatus(MediaFile file, boolean inRedis) {

        if (inRedis || !TranscriptStatus.PROCESSING.equals(TranscriptStatusHelper.resolve(file))) {

            return;

        }

        String inferred = TranscriptStatusHelper.inferFromText(file.getTranscriptText());

        if (TranscriptStatus.PROCESSING.equals(inferred)) {

            inferred = TranscriptStatus.NONE;

        }

        System.out.println("⚠️ [转写状态修复] mediaId=" + file.getId()

                + " PROCESSING -> " + inferred + " (后台任务已结束)");

        file.setTranscriptStatus(inferred);

        file.setTranscribing(false);

        mediaFileMapper.updateById(file);

    }



    /** @deprecated 请使用 {@link TranscriptStatusHelper#isReady(MediaFile)} */

    @Deprecated

    public static boolean isInvalidTranscript(String text) {

        return !TranscriptStatus.OK.equals(TranscriptStatusHelper.inferFromText(text));

    }



    private boolean runTranscribePipeline(MediaFile mediaFile, boolean forceReindex) {

        Long mediaId = mediaFile.getId();

        String text = aiAnalysisStrategy.transcribe(mediaFile.getFilePath());

        TranscriptStatusHelper.applyResult(mediaFile, text);

        mediaFileMapper.updateById(mediaFile);

        if (TranscriptStatusHelper.isReady(mediaFile) && forceReindex) {

            ragIndexService.deleteChunks(mediaId);

        }

        return TranscriptStatusHelper.isReady(mediaFile);

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


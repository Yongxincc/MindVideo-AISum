package com.example.server.consumer;

import com.example.server.dto.AnalysisTaskMsg;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.pipeline.PipelineStage;
import com.example.server.service.AiService;
import com.example.server.service.ContentDedupService;
import com.example.server.service.PipelineTraceService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RocketMQMessageListener(topic = "video-analysis-topic", consumerGroup = "video-group")
public class VideoAnalysisConsumer implements RocketMQListener<AnalysisTaskMsg> {

    @Autowired
    private AiService aiService;

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ContentDedupService contentDedupService;

    @Autowired
    private PipelineTraceService pipelineTrace;

    @Override
    public void onMessage(AnalysisTaskMsg msg) {
        Long mediaId = msg.getMediaId();
        System.out.println("⚡ [MQ消费者] 收到任务 ID: " + mediaId);
        pipelineTrace.stageStart(mediaId, PipelineStage.MQ_CONSUME, "START_ANALYSIS");

        MediaFile file = mediaFileMapper.selectById(mediaId);
        String lockKey = contentDedupService.resolveLockKey(file, "lock:analyze:" + mediaId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, -1, TimeUnit.SECONDS)) {
                System.out.println("⚠️ [MQ消费者] 同内容任务处理中，跳过 mediaId=" + mediaId);
                return;
            }
            aiService.asyncAnalyze(mediaId);
            pipelineTrace.stageEnd(mediaId, PipelineStage.MQ_CONSUME, true, "已提交线程池", null);
        } catch (Exception e) {
            e.printStackTrace();
            pipelineTrace.stageEnd(mediaId, PipelineStage.MQ_CONSUME, false, e.getMessage(), null);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

package com.example.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.constant.TranscriptStatus;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.util.TranscriptStatusHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ContentDedupService {

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private RagIndexService ragIndexService;

    /**
     * 若库中已有相同 MD5 且转写完成，则复制转写结果到当前记录（跳过 ASR）
     *
     * @return true 表示已复用，调用方应跳过 transcribe
     */
    public boolean tryReuseTranscript(MediaFile target) {
        if (target == null || target.getContentMd5() == null || target.getContentMd5().isBlank()) {
            return false;
        }
        if (TranscriptStatusHelper.isReady(target)) {
            return true;
        }

        QueryWrapper<MediaFile> q = new QueryWrapper<>();
        q.eq("content_md5", target.getContentMd5())
                .eq("transcript_status", TranscriptStatus.OK)
                .ne("id", target.getId())
                .orderByAsc("id")
                .last("LIMIT 1");

        MediaFile canonical = mediaFileMapper.selectOne(q);
        if (canonical == null || !TranscriptStatusHelper.isReady(canonical)) {
            return false;
        }

        target.setTranscriptText(canonical.getTranscriptText());
        TranscriptStatusHelper.applyResult(target, canonical.getTranscriptText());
        mediaFileMapper.updateById(target);

        ragIndexService.copyIndexFrom(canonical.getId(), target.getId());
        System.out.println("♻️ [Dedup] 复用 MD5=" + target.getContentMd5()
                + " 来自 mediaId=" + canonical.getId() + " → " + target.getId());
        return true;
    }

    public String resolveLockKey(MediaFile mediaFile, String fallbackMediaIdKey) {
        if (mediaFile != null && mediaFile.getContentMd5() != null && !mediaFile.getContentMd5().isBlank()) {
            return "lock:content:" + mediaFile.getContentMd5();
        }
        return fallbackMediaIdKey;
    }
}

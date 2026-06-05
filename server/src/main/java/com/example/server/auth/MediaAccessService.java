package com.example.server.auth;

import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MediaAccessService {

    @Autowired
    private MediaFileMapper mediaFileMapper;

    public MediaFile requireOwnedMedia(Long mediaId, Long userId) {
        if (mediaId == null) {
            throw new ForbiddenException("缺少媒体 ID");
        }
        MediaFile media = mediaFileMapper.selectById(mediaId);
        if (media == null) {
            throw new ForbiddenException("文件不存在");
        }
        requireOwner(media, userId);
        return media;
    }

    public void requireOwner(MediaFile media, Long userId) {
        if (media == null) {
            throw new ForbiddenException("文件不存在");
        }
        if (media.getUserId() == null || userId == null || !media.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该文件");
        }
    }
}

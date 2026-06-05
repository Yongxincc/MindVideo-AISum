package com.example.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("transcript_chunks")
public class TranscriptChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;
    private Integer chunkIndex;
    private String content;
    /** JSON array of floats stored as string */
    private String embedding;
    private Integer startOffset;
    private Integer endOffset;
    private LocalDateTime createdAt;
}

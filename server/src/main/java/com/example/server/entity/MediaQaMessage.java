package com.example.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_qa_messages")
public class MediaQaMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;
    private String question;
    private String answer;
    /** JSON array of CitationDto */
    private String citationsJson;
    /** OK | FAILED */
    private String status;
    private LocalDateTime createdAt;
}

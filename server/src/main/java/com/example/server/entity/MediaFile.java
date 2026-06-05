package com.example.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("media_files")
public class MediaFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;          // 核心：记录是谁传的

    private String filename;
    private String displayName;
    private String status;        //UPLOADED, COMPLETED
    private String filePath;

    //下面这几个是新加的
    private String aiSummary;
    private String transcriptText;
    /** NONE | PROCESSING | OK | FAILED */
    private String transcriptStatus;
    /** 仅 API 返回：后台是否正在转写（不落库，与 Redis 同步） */
    @TableField(exist = false)
    private Boolean transcribing;
    private String coverUrl;

    /** 文件内容 MD5，用于去重与分布式锁 */
    private String contentMd5;
    /** RAG 向量索引完成时间 */
    private LocalDateTime ragIndexedAt;
    /** 建立 RAG 索引时使用的 Embedding 模型名，用于检测模型变更后自动重建 */
    private String ragEmbedModel;

    //【修改点】删掉了 @TableField(fill = ...) 注解
    //上传时间由数据库自动记录，Java 不插手，防止报错
    private LocalDateTime uploadTime;
}
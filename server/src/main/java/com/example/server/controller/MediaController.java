package com.example.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.auth.AuthContext;
import com.example.server.auth.MediaAccessService;
import com.example.server.dto.AskRequestDto;
import com.example.server.dto.UploadResultDto;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.service.AiService;
import com.example.server.service.ContentDedupService;
import com.example.server.service.MediaService;
import com.example.server.pipeline.PipelineTraceContext;
import com.example.server.service.PipelineTraceService;
import com.example.server.dto.AskStatusDto;
import com.example.server.dto.MediaQaMessageDto;
import com.example.server.service.MediaQaService;
import com.example.server.service.RagAskService;
import com.example.server.util.ContentHashUtil;
import com.example.server.utils.MinioUtils;
import com.example.server.utils.YtDlpUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/media")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MediaController {

    @Autowired(required = false)
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioUtils minioUtils;

    @Autowired
    private YtDlpUtils ytDlpUtils;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private AiService aiService;

    @Autowired
    private ContentDedupService contentDedupService;

    @Autowired
    private RagAskService ragAskService;

    @Autowired
    private MediaQaService mediaQaService;

    @Autowired
    private PipelineTraceService pipelineTrace;

    @Autowired
    private MediaAccessService mediaAccessService;

    @PostMapping("/init-upload")
    public ResponseEntity<String> initUpload() {
        AuthContext.requireUserId();
        String uploadId = mediaService.initChunkedUpload();
        return ResponseEntity.ok(uploadId);
    }

    @PostMapping("/upload-chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("file") MultipartFile chunk) {
        AuthContext.requireUserId();
        try {
            mediaService.saveChunk(uploadId, chunkIndex, totalChunks, chunk);
            return ResponseEntity.ok("chunk ok");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("chunk failed: " + e.getMessage());
        }
    }

    @GetMapping("/upload-status")
    public Map<String, Object> uploadStatus(@RequestParam("uploadId") String uploadId) {
        AuthContext.requireUserId();
        return mediaService.getUploadStatus(uploadId);
    }

    @PostMapping("/merge-chunks")
    public ResponseEntity<Map<String, Object>> mergeChunks(
            @RequestParam("uploadId") String uploadId,
            @RequestParam(value = "filename", required = false) String filename) {
        Long userId = AuthContext.requireUserId();
        try {
            UploadResultDto result = mediaService.mergeChunks(uploadId, userId, filename);
            if (result.getMediaId() != null) {
                MediaFile latest = mediaFileMapper.selectById(result.getMediaId());
                if (latest != null) {
                    contentDedupService.tryReuseTranscript(latest);
                }
            }
            invalidateListCache(userId);
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Upload success");
            body.put("fileUrl", result.getFileUrl());
            body.put("contentMd5", result.getContentMd5());
            body.put("mediaId", result.getMediaId());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "merge failed: " + e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        Long userId = AuthContext.requireUserId();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Upload failed: file is empty");
        }
        if (mediaFileMapper == null) {
            return ResponseEntity.status(500).body("Upload failed: database not ready");
        }
        try {
            UploadResultDto uploaded = mediaService.uploadMultipartWithHash(file);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setFilename(file.getOriginalFilename());
            mediaFile.setFilePath(uploaded.getFileUrl());
            mediaFile.setContentMd5(uploaded.getContentMd5());
            mediaFile.setStatus("COMPLETED");
            mediaFile.setUploadTime(LocalDateTime.now());
            mediaFile.setUserId(userId);

            mediaFileMapper.insert(mediaFile);
            boolean reused = contentDedupService.tryReuseTranscript(mediaFile);
            invalidateListCache(userId);

            String hint = reused
                    ? "Upload success (detected duplicate content, transcript reused)"
                    : "Upload success";
            return ResponseEntity.ok(hint);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/upload-url")
    public ResponseEntity<String> uploadUrl(@RequestParam("url") String url) {
        Long userId = AuthContext.requireUserId();
        File tempFile = null;
        try {
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body("Upload failed: url is empty");
            }
            if (mediaFileMapper == null) {
                return ResponseEntity.status(500).body("Upload failed: database not ready");
            }

            tempFile = ytDlpUtils.downloadVideo(url);
            String contentMd5 = ContentHashUtil.md5Hex(tempFile.toPath());
            String fileUrl = minioUtils.uploadLocalFile(tempFile);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setFilename("WEB_" + tempFile.getName());
            mediaFile.setFilePath(fileUrl);
            mediaFile.setContentMd5(contentMd5);
            mediaFile.setStatus("COMPLETED");
            mediaFile.setUploadTime(LocalDateTime.now());
            mediaFile.setUserId(userId);

            mediaFileMapper.insert(mediaFile);
            pipelineTrace.beginTask(mediaFile.getId(), "upload");
            PipelineTraceContext.set(mediaFile.getId());
            contentDedupService.tryReuseTranscript(mediaFile);
            invalidateListCache(userId);

            return ResponseEntity.ok("Upload success");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        } finally {
            PipelineTraceContext.clear();
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<AskStatusDto> ask(@RequestBody AskRequestDto request) {
        Long userId = AuthContext.requireUserId();
        if (request.getMediaId() == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        mediaAccessService.requireOwnedMedia(request.getMediaId(), userId);
        AskStatusDto status = ragAskService.submit(request.getMediaId(), request.getQuestion().trim());
        return ResponseEntity.accepted().body(status);
    }

    @GetMapping("/ask-status")
    public ResponseEntity<AskStatusDto> askStatus(@RequestParam Long mediaId) {
        Long userId = AuthContext.requireUserId();
        mediaAccessService.requireOwnedMedia(mediaId, userId);
        return ResponseEntity.ok(ragAskService.getStatus(mediaId));
    }

    @GetMapping("/ask-history")
    public ResponseEntity<List<MediaQaMessageDto>> askHistory(@RequestParam Long mediaId) {
        Long userId = AuthContext.requireUserId();
        mediaAccessService.requireOwnedMedia(mediaId, userId);
        return ResponseEntity.ok(mediaQaService.listHistory(mediaId));
    }

    /** messageId 有值时删单条；否则清空该视频全部问答历史 */
    @DeleteMapping("/ask-history")
    public ResponseEntity<java.util.Map<String, Integer>> deleteAskHistory(
            @RequestParam Long mediaId,
            @RequestParam(required = false) Long messageId) {
        Long userId = AuthContext.requireUserId();
        mediaAccessService.requireOwnedMedia(mediaId, userId);
        if (messageId != null) {
            if (!mediaQaService.deleteMessage(mediaId, messageId)) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(java.util.Map.of("deleted", 1));
        }
        int deleted = mediaQaService.deleteAllByMediaId(mediaId);
        return ResponseEntity.ok(java.util.Map.of("deleted", deleted));
    }

    @GetMapping("/list")
    public List<MediaFile> getList(@RequestParam(value = "_t", required = false) String cacheBuster) {
        Long userId = AuthContext.requireUserId();
        String cacheKey = "media:list:user:" + userId;
        boolean useCache = cacheBuster == null || cacheBuster.isBlank();

        if (useCache) {
            try {
                String json = redisTemplate.opsForValue().get(cacheKey);
                if (json != null) {
                    List<MediaFile> cached = objectMapper.readValue(json, new TypeReference<List<MediaFile>>() {});
                    aiService.enrichTranscribingFlags(cached);
                    return cached;
                }
            } catch (Exception e) {
                System.err.println("Redis 读取失败: " + e.getMessage());
            }
        }

        QueryWrapper<MediaFile> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        List<MediaFile> list = mediaFileMapper.selectList(query.orderByDesc("id"));
        aiService.enrichTranscribingFlags(list);

        try {
            String jsonToWrite = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(cacheKey, jsonToWrite, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    @DeleteMapping("/delete")
    public String delete(@RequestParam("id") Long id) {
        Long userId = AuthContext.requireUserId();
        MediaFile media = mediaFileMapper.selectById(id);
        if (media == null) return "文件不存在";

        mediaAccessService.requireOwner(media, userId);

        if (media.getFilePath() != null && media.getFilePath().startsWith("http")) {
            minioUtils.removeFile(media.getFilePath());
        }

        mediaFileMapper.deleteById(id);
        invalidateListCache(userId);
        return "删除成功";
    }

    @PostMapping("/rename")
    public ResponseEntity<String> rename(@RequestParam("id") Long id,
                                         @RequestParam("displayName") String displayName) {
        Long userId = AuthContext.requireUserId();
        if (mediaFileMapper == null) {
            return ResponseEntity.status(500).body("数据库未就绪");
        }

        MediaFile media = mediaFileMapper.selectById(id);
        if (media == null) {
            return ResponseEntity.badRequest().body("文件不存在");
        }

        mediaAccessService.requireOwner(media, userId);

        String trimmed = displayName == null ? "" : displayName.trim();
        if (trimmed.length() > 128) {
            return ResponseEntity.badRequest().body("名称不能超过 128 个字符");
        }

        media.setDisplayName(trimmed.isEmpty() ? null : trimmed);
        mediaFileMapper.updateById(media);
        invalidateListCache(userId);

        return ResponseEntity.ok("重命名成功");
    }

    private void invalidateListCache(Long userId) {
        if (userId != null) {
            redisTemplate.delete("media:list:user:" + userId);
        }
    }
}

package com.example.server.service;

import com.example.server.dto.UploadResultDto;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.util.ContentHashUtil;
import com.example.server.utils.MinioUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MediaService {

    private static final String CHUNK_UPLOAD_KEY_PREFIX = "upload:chunked:";
    private static final String CHUNK_META_PREFIX = "upload:meta:";

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MinioUtils minioUtils;

    private final Path chunkBaseDir;

    public MediaService() {
        chunkBaseDir = Path.of(System.getProperty("java.io.tmpdir"), "mindvideo-chunks");
        try {
            Files.createDirectories(chunkBaseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create chunk dir", e);
        }
    }

    public String initChunkedUpload() {
        String uploadId = UUID.randomUUID().toString();
        String redisKey = CHUNK_UPLOAD_KEY_PREFIX + uploadId;
        redisTemplate.opsForValue().set(redisKey + ":status", "INIT", 1, TimeUnit.DAYS);
        return uploadId;
    }

    public void saveChunk(String uploadId, int chunkIndex, int totalChunks, MultipartFile chunk) throws IOException {
        if (!redisTemplate.hasKey(CHUNK_UPLOAD_KEY_PREFIX + uploadId + ":status")) {
            throw new IllegalArgumentException("无效的 uploadId");
        }
        Path dir = chunkBaseDir.resolve(uploadId);
        Files.createDirectories(dir);
        Path partFile = dir.resolve(String.valueOf(chunkIndex));
        chunk.transferTo(partFile.toFile());

        String hashKey = CHUNK_UPLOAD_KEY_PREFIX + uploadId;
        redisTemplate.opsForHash().put(hashKey, String.valueOf(chunkIndex), "1");
        redisTemplate.expire(hashKey, 1, TimeUnit.DAYS);

        redisTemplate.opsForValue().set(
                CHUNK_META_PREFIX + uploadId,
                totalChunks + "|" + (chunk.getOriginalFilename() != null ? chunk.getOriginalFilename() : "video.mp4"),
                1,
                TimeUnit.DAYS);
    }

    public Map<String, Object> getUploadStatus(String uploadId) {
        String hashKey = CHUNK_UPLOAD_KEY_PREFIX + uploadId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(hashKey);
        List<Integer> uploaded = entries.keySet().stream()
                .map(k -> Integer.parseInt(k.toString()))
                .sorted()
                .collect(Collectors.toList());

        int totalChunks = 0;
        String meta = redisTemplate.opsForValue().get(CHUNK_META_PREFIX + uploadId);
        if (meta != null && meta.contains("|")) {
            totalChunks = Integer.parseInt(meta.split("\\|")[0]);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("uploadId", uploadId);
        result.put("uploadedChunks", uploaded);
        result.put("totalChunks", totalChunks);
        return result;
    }

    public UploadResultDto mergeChunks(String uploadId, Long userId, String filename) throws Exception {
        Map<String, Object> status = getUploadStatus(uploadId);
        int totalChunks = (int) status.get("totalChunks");
        @SuppressWarnings("unchecked")
        List<Integer> uploaded = (List<Integer>) status.get("uploadedChunks");

        if (totalChunks <= 0) {
            throw new IllegalArgumentException("缺少分片元数据，请重新 init-upload");
        }
        if (uploaded.size() != totalChunks) {
            throw new IllegalStateException("分片未齐全: " + uploaded.size() + "/" + totalChunks);
        }

        Path dir = chunkBaseDir.resolve(uploadId);
        File merged = File.createTempFile("merged_", ".mp4");
        try (FileOutputStream out = new FileOutputStream(merged)) {
            for (int i = 0; i < totalChunks; i++) {
                Path part = dir.resolve(String.valueOf(i));
                if (!Files.exists(part)) {
                    throw new IllegalStateException("缺失分片: " + i);
                }
                Files.copy(part, out);
            }
        }

        String contentMd5 = ContentHashUtil.md5Hex(merged.toPath());
        String displayName = filename != null && !filename.isBlank() ? filename : "chunked_upload.mp4";
        String fileUrl = minioUtils.uploadLocalFile(merged);

        MediaFile mediaFile = new MediaFile();
        mediaFile.setFilename(displayName);
        mediaFile.setFilePath(fileUrl);
        mediaFile.setStatus("COMPLETED");
        mediaFile.setContentMd5(contentMd5);
        mediaFile.setUploadTime(LocalDateTime.now());
        if (userId != null) {
            mediaFile.setUserId(userId);
        }
        mediaFileMapper.insert(mediaFile);

        cleanupChunkSession(uploadId);
        merged.delete();
        return new UploadResultDto(mediaFile.getId(), fileUrl, contentMd5);
    }

    private void cleanupChunkSession(String uploadId) throws IOException {
        redisTemplate.delete(CHUNK_UPLOAD_KEY_PREFIX + uploadId);
        redisTemplate.delete(CHUNK_UPLOAD_KEY_PREFIX + uploadId + ":status");
        redisTemplate.delete(CHUNK_META_PREFIX + uploadId);
        Path dir = chunkBaseDir.resolve(uploadId);
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    public UploadResultDto uploadMultipartWithHash(MultipartFile file) throws Exception {
        String suffix = ".mp4";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            suffix = original.substring(original.lastIndexOf('.'));
        }
        File temp = File.createTempFile("upload_", suffix);
        try {
            file.transferTo(temp);
            String md5 = ContentHashUtil.md5Hex(temp.toPath());
            String url;
            try (FileInputStream in = new FileInputStream(temp)) {
                url = minioUtils.uploadStream(in, temp.length(), original, file.getContentType());
            }
            return new UploadResultDto(url, md5);
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }
}

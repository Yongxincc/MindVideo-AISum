package com.example.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadResultDto {
    private Long mediaId;
    private String fileUrl;
    private String contentMd5;

    public UploadResultDto(String fileUrl, String contentMd5) {
        this(null, fileUrl, contentMd5);
    }
}

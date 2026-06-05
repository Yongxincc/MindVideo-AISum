package com.example.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaQaMessageDto {
    private Long id;
    private String question;
    private String answer;
    private List<CitationDto> citations;
    private String status;
    private LocalDateTime createdAt;
}

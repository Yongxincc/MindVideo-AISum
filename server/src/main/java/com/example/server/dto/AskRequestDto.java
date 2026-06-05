package com.example.server.dto;

import lombok.Data;

@Data
public class AskRequestDto {
    private Long mediaId;
    private Long userId;
    private String question;
}

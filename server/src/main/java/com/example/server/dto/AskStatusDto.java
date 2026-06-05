package com.example.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskStatusDto {
    /** RUNNING | DONE | FAILED | IDLE */
    private String status;
    private String answer;
    private List<CitationDto> citations;
    private String message;
}

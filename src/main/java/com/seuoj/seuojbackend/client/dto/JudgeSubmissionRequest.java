package com.seuoj.seuojbackend.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交测评
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JudgeSubmissionRequest {
    private String submissionId;
    private String pid;
    private String code;
    private String language;
}

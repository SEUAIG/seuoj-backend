package com.seuoj.seuojbackend.vo.submission;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SubmissionResultVO {
    /**
     * 提交记录业务编号
     */
    private String submissionNo;

    /**
     * 题目编号
     */
    private String pid;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 评测状态：PENDING/RUNNING/AC/WA/TLE/RE/CE
     */
    private String status;

    /**
     * 详细评测结果（JSON 结构）
     */
    private String resultDetail;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 评测完成时间
     */
    private LocalDateTime finishTime;
}

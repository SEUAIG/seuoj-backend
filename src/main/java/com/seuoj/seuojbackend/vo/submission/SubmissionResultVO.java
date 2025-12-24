package com.seuoj.seuojbackend.vo.submission;

import java.time.LocalDateTime;
import java.util.List;

import com.seuoj.seuojbackend.model.vo.JudgeResultDetailItem;
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
     * 提交流程状态（Pending/Running/Finished/Failed）
     */
    private String status;

    /**
     * 最终判定结果（Accepted/WA/TLE/.../CompileError/JudgeError）
     */
    private String verdict;

    /**
     * 详细评测结果
     */
    private List<JudgeResultDetailItem> resultDetail;

    /**
     * 编译/题目错误详情
     */
    private String errorDetail;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 评测完成时间
     */
    private LocalDateTime finishTime;
}

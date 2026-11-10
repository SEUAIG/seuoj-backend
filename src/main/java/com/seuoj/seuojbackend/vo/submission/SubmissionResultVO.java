package com.seuoj.seuojbackend.vo.submission;

import java.time.LocalDateTime;
import java.util.List;

import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

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

    private Long assignmentId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long contestId;

    private Long maxTime;
    private Long maxMemory;
    private Boolean testDataVisible;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<JudgeResultDetailItem> resultDetail;

    /**
     * 子任务列表
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProblemConfigDTO.Subtask> subtasks;

    /**
     * 编译/题目错误详情
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String errorDetail;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 评测完成时间
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime finishTime;

    /**
     * 分数
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer score;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 提交者用户ID
     */
    private Long userId;

    /**
     * 提交者用户名
     */
    private String username;

    /**
     * 提交者昵称
     */
    private String nickname;
    /** 被管理者跳过：verdict 显示为 Skipped，不计入任何统计 */
    private boolean skipped;

    private String originalVerdict;

    private String skipReason;

    /** 当前用户能否 skip / 取消 skip 这条提交 */
    private boolean canSkip;
}

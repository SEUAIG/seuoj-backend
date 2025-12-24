package com.seuoj.seuojbackend.dto.judge;

import com.seuoj.seuojbackend.common.SubmitExecStatus;
import com.seuoj.seuojbackend.model.vo.JudgeResultDetailItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import lombok.Data;

@Data
public class JudgeResultDTO {
    /**
     * 评测状态string，Success、CompileError、JudgendError
     */
    @NotNull(message = "status 不可为空")
    private SubmitExecStatus status;

    /**
     * 每一个测试点的输出（编译错误或测评错误没有这个）
     */
    @Valid
    private List<JudgeResultDetailItem> resultDetail;

    /**
     * 如果status是CompileError或JudgendError，错误信息在这
     */
    private String errorDetail;

    // TODO:需要密钥验证来源
}

package com.seuoj.seuojbackend.dto.judge;

import com.seuoj.seuojbackend.model.vo.JudgeResultDetailItem;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class JudgeResultDTO {
    /** 评测状态string，Success、CompileError、JudgendError */
    @NotBlank(message = "status 不可为空")
    private String status;

    /** 每一个测试点的输出（编译错误或测评错误没有这个） */
    private List<JudgeResultDetailItem> resultDetail;

    /** 提交编号 */
    @NotBlank(message = "submissionNo 不可为空")
    private String submissionNo;

    /** 如果status是CompileError或JudgendError，错误信息在这 */
    private String errorDetail;

    // TODO:需要密钥验证来源
}
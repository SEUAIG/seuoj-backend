package com.seuoj.seuojbackend.dto.judge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JudgeResultDTO {
    /**
     * 评测结果状态
     */
    @NotBlank(message = "status 不能为空")
    private String status;

    /**
     * 详细评测结果（JSON 字符串）
     * 例如：{"time": 120, "memory": 1024, "testCases": [...]}
     */
    private String resultDetail;

    // TODO:需要密钥验证来源
}

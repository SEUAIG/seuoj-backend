package com.seuoj.seuojbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitDTO {

    @NotBlank(message = "题目编号不能为空")
    private String pid;

    @NotBlank(message = "编程语言不能为空")
    @Pattern(
            regexp = "^(C|Cpp|Cpp11|Cpp17|Cpp20|Python3_12|Nodejs22|Go1_22|Java17)$",
            message = "不支持的编程语言"
    )
    private String language;

    @NotBlank(message = "代码不能为空")
    @Size(max = 65535, message = "代码长度不能超过65535字符")
    private String code;
}

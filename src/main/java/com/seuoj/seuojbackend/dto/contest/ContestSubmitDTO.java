package com.seuoj.seuojbackend.dto.contest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ContestSubmitDTO {

    @NotBlank(message = "pid 不能为空")
    private String pid;

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "language 不能为空")
    @Pattern(
            regexp = "^(C|Cpp|Cpp20|Python|Nodejs|Go|Java)$",
            message = "language 不支持"
    )
    private String language;
}

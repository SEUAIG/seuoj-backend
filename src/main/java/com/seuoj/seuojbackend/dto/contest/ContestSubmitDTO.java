package com.seuoj.seuojbackend.dto.contest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContestSubmitDTO {

    @NotBlank(message = "pid 不能为空")
    private String pid;

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "language 不能为空")
    private String language;
}

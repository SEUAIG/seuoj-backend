package com.seuoj.seuojbackend.dto.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class OnlineJudgeSubmitDTO {
    @NotBlank(message = "pid 不能为空")
    private String pid;

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "language 不能为空")
    private String language;

    @Valid
    @NotEmpty(message = "testcases 不能为空")
    private List<TestcaseItem> testcases;

    @Data
    public static class TestcaseItem {
        @NotNull(message = "testcase.id 不能为空")
        private Integer id;

        @NotNull(message = "testcase.in 不能为空")
        private String in;
    }
}

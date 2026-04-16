package com.seuoj.seuojbackend.dto.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ClassBatchImportDTO {

    @NotNull(message = "密码模式不能为空")
    @Pattern(regexp = "^(assigned|random)$", message = "密码模式只能是 assigned 或 random")
    @JsonProperty("password_mode")
    private String passwordMode;

    @JsonProperty("send_email")
    private boolean sendEmail = false;

    @NotEmpty(message = "学生列表不能为空")
    @Size(max = 500, message = "单次最多导入500个学生")
    @Valid
    private List<StudentRow> students;

    @Data
    public static class StudentRow {
        @NotBlank(message = "一卡通号不能为空")
        @Size(max = 64, message = "一卡通号最长64个字符")
        @JsonProperty("student_id")
        private String studentId;

        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名最长64个字符")
        private String name;

        @Size(min = 6, max = 64, message = "密码长度6-64个字符")
        private String password;
    }
}

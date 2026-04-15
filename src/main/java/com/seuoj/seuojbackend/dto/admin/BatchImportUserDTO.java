package com.seuoj.seuojbackend.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchImportUserDTO {

    /**
     * 密码模式: "assigned" = 指定密码, "random" = 随机生成
     */
    @NotNull(message = "密码模式不能为空")
    @Pattern(regexp = "^(assigned|random)$", message = "密码模式只能是 assigned 或 random")
    private String passwordMode;

    /**
     * 导入成功后是否给用户发送邮件通知
     */
    private boolean sendEmail = false;

    @NotEmpty(message = "用户列表不能为空")
    @Size(max = 500, message = "单次最多导入500个用户")
    @Valid
    private List<UserRow> users;

    @Data
    public static class UserRow {
        @NotEmpty(message = "用户名不能为空")
        @Size(max = 64, message = "用户名最长64个字符")
        private String username;

        @NotEmpty(message = "邮箱不能为空")
        @Size(max = 128, message = "邮箱最长128个字符")
        private String email;

        /**
         * 指定密码模式下必填，随机密码模式下忽略
         */
        @Size(min = 6, max = 64, message = "密码长度6-64个字符")
        private String password;
    }
}

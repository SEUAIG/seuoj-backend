package com.seuoj.seuojbackend.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileDTO {
    @Size(max = 64, message = "昵称最长64个字符")
    private String nickname;
}

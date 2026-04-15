package com.seuoj.seuojbackend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDTO {

    @NotBlank
    @JsonProperty("old_password")
    private String oldPassword;

    @NotBlank
    @Size(min = 6, max = 20)
    @JsonProperty("new_password")
    private String newPassword;
}

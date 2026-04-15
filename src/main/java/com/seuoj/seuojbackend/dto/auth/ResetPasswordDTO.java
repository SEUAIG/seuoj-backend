package com.seuoj.seuojbackend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    @NotBlank
    @Email
    @Size(max = 128)
    private String email;

    @NotBlank
    @JsonProperty("verification_id")
    private String verificationId;

    @NotBlank
    private String code;

    @NotBlank
    @Size(min = 6, max = 20)
    @JsonProperty("new_password")
    private String newPassword;
}

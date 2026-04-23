package com.seuoj.seuojbackend.dto.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AttachmentDTO {

    @NotBlank(message = "file_path 不能为空")
    @JsonProperty("file_path")
    private String filePath;

    @NotBlank(message = "file_name 不能为空")
    @JsonProperty("file_name")
    private String fileName;

    @Positive(message = "file_size 必须为正整数")
    @JsonProperty("file_size")
    private Long fileSize;
}

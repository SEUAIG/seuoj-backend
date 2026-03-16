package com.seuoj.seuojbackend.dto.tag;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagCreateDTO {
    @NotBlank(message = "tag_name 不能为空")
    @JsonProperty("tag_name")
    private String tagName;

    @NotBlank(message = "category_name 不能为空")
    @JsonProperty("category_name")
    private String categoryName;
}

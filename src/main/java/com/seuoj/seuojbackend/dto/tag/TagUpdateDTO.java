package com.seuoj.seuojbackend.dto.tag;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TagUpdateDTO {
    @NotNull(message = "tag_id 不能为空")
    @JsonProperty("tag_id")
    private Long tagId;

    @JsonProperty("tag_name")
    private String tagName;

    @JsonProperty("category_name")
    private String categoryName;
}

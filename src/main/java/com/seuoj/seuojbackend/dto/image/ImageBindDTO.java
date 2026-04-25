package com.seuoj.seuojbackend.dto.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ImageBindDTO {

    @JsonProperty("resource_type")
    private String resourceType;

    @JsonProperty("resource_id")
    private Long resourceId;

    @JsonProperty("image_keys")
    @Size(max = 200, message = "一次最多绑定 200 张图片")
    private List<String> imageKeys;
}


package com.seuoj.seuojbackend.vo.tag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 标签项
 */
@Data
public class TagItemVO {
    @JsonProperty("tag_id")
    private Long tagId;

    @JsonProperty("tag_name")
    private String tagName;
}

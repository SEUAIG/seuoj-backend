package com.seuoj.seuojbackend.vo.tag;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 标签列表响应
 */
@Data
public class TagListVO {

    private List<TagGroup> algorithm;

    private List<TagGroup> source;

    private List<TagGroup> time;

    private List<TagGroup> special;

    @Data
    public static class TagGroup {
        @JsonProperty("group_name")
        private String groupName;

        private List<TagItemVO> tags;
    }
}

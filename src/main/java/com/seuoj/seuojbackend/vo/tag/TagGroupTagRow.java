package com.seuoj.seuojbackend.vo.tag;

import lombok.Data;

/**
 * 标签分组与标签行数据
 */
@Data
public class TagGroupTagRow {

    private Long groupId;

    private String groupType;

    private String groupName;

    private Long tagId;

    private String tagName;
}

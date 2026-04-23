package com.seuoj.seuojbackend.vo.classinfo;

import lombok.Data;

@Data
public class LinkPageItemVO {

    /**
     * 关联对象 ID
     */
    private Long id;

    /**
     * 关联对象标题
     */
    private String title;
}
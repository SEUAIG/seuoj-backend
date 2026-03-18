package com.seuoj.seuojbackend.vo.classinfo;

import java.util.List;
import lombok.Data;

@Data
public class ClassMemberPageVO {

    /**
     * 当前页
     */
    private Long current;

    /**
     * 每页条数
     */
    private Long size;

    /**
     * 总条数
     */
    private Long total;

    /**
     * 成员记录
     */
    private List<ClassMemberItemVO> records;
}
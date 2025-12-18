package com.seuoj.seuojbackend.vo.problem;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/*
* 问题详情VO
*/
@Data
public class ProblemDetailVO {
    private String pid;
    private String title;
    /*
     * 该字段需要向评测端发请求获取
     */
    private String content;
    /*
     * 该字段需要查数据库获取
     */
    private List<String> tags;
    private Integer totalSubmit;
    private Integer totalAccept;
    private String acceptRate;
    private LocalDateTime createdAt;
}

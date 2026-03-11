package com.seuoj.seuojbackend.vo.problem;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import lombok.Data;

/*
* 问题详情VO
*/
@Data
public class ProblemDetailVO {
    private String pid;
    private String title;

    @JsonProperty("is_public")
    private Boolean isPublic;

    /*
     * 该字段需要向评测端发请求获取
     */
    private ProblemContentDTO content;
    /*
     * 该字段需要查数据库获取
     */
    private List<String> tags;
    private Integer totalSubmit;
    private Integer totalAccept;
}

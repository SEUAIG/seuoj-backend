package com.seuoj.seuojbackend.dto.contest;

import lombok.Data;

/** 配色面板查询行：题目 + 显式配置的颜色（可能为空）。 */
@Data
public class ContestBalloonProblemRow {

    private String pid;
    private String title;
    private String balloonColor;
    private Integer sortIndex;
}

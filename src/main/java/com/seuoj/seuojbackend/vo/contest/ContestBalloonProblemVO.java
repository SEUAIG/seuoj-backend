package com.seuoj.seuojbackend.vo.contest;

import lombok.Data;

/** 配色面板中的一道题：当前生效颜色，以及是否为显式配置。 */
@Data
public class ContestBalloonProblemVO {

    private String pid;
    private String title;
    /** 生效颜色 #RRGGBB：显式配置优先，否则为按题目顺序的默认调色板色 */
    private String color;
    /** true 表示该题显式配置过颜色 */
    private boolean explicit;
}

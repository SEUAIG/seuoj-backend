package com.seuoj.seuojbackend.dto.contest;

/** 设置某道题的气球颜色；color 为空表示恢复自动分配。 */
public record ContestBalloonColorDTO(String pid, String color) {
}

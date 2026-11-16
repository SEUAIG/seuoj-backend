package com.seuoj.seuojbackend.vo.contest;

import java.time.LocalDateTime;
import lombok.Data;

/** 气球队列中的一行。 */
@Data
public class ContestBalloonItemVO {

    private Long id;
    private Long problemId;
    /** 题号，如 P1001 */
    private String pid;
    private String title;
    /** #RRGGBB */
    private String color;
    /** PENDING / DELIVERED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private String username;
    private String nickname;
}

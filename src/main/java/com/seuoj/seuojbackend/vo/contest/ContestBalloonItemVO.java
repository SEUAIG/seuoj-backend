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
    /** 对应提交已被重测改判或 skip（只会出现在已送达的气球上，待送达的会直接撤出队列） */
    private Boolean revoked;
    private String username;
    private String nickname;
}

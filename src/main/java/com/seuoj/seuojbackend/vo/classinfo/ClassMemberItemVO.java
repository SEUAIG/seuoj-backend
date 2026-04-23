package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ClassMemberItemVO {

    /**
     * 用户 ID
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    private String nickname;

    /**
     * 加入时间
     */
    @JsonProperty("joined_at")
    private LocalDateTime joinedAt;
}
package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ClassMemberItemVO {

    /**
     * 用户公开 ID
     */
    @JsonProperty("user_public_id")
    private String userPublicId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 加入时间
     */
    @JsonProperty("joined_at")
    private LocalDateTime joinedAt;
}
package com.seuoj.seuojbackend.vo.permission;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResourcePermissionVO {

    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    private String username;

    private String nickname;

    private String permission;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}

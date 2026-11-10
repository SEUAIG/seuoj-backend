package com.seuoj.seuojbackend.vo.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class UserPageItemVO {

    @JsonProperty("user_id")
    private Long userId;

    private String username;

    private String nickname;

    private String email;
    private boolean canManageProfile;

    /** 当前是否处于封禁中（已到期的封禁视为未封禁） */
    private boolean banned;
    private String banReason;
    /** 空表示永久 */
    private LocalDateTime bannedUntil;
    private LocalDateTime bannedAt;

    @JsonProperty("roles")
    private List<String> roles;
}

package com.seuoj.seuojbackend.vo.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class UserPageItemVO {

    @JsonProperty("user_id")
    private Long userId;

    private String username;

    private String nickname;

    private String email;

    @JsonProperty("roles")
    private List<String> roles;
}
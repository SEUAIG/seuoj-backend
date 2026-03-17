package com.seuoj.seuojbackend.vo.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class UserPageItemVO {

    @JsonIgnore
    private Long userId;

    @JsonProperty("user_public_id")
    private String userPublicId;

    private String username;

    private String email;

    @JsonProperty("roles")
    private List<String> roles;
}
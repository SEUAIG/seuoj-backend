package com.seuoj.seuojbackend.vo.user;

import lombok.Data;

@Data
public class UserMeVO {
    private String role;
    private java.util.Set<com.seuoj.seuojbackend.common.Capability> capabilities;
    private Boolean allowNicknameChange;
    private Long id;
    private String username;
    private String nickname;
    private String email;
}

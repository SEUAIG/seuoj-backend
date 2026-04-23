package com.seuoj.seuojbackend.vo.user;

import lombok.Data;

@Data
public class UserProfileVO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
}

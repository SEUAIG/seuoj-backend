package com.seuoj.seuojbackend.vo.user;

import lombok.Data;

@Data
public class UserMeVO {
    private Long id;
    private String username;
    private String nickname;
    private String email;
}

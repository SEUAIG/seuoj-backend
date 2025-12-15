package com.seuoj.seuojbackend.common;

import lombok.Getter;

/**
 * 角色类型枚举（初步）
 */
@Getter
public enum RoleType {
    ADMIN("ADMIN", "管理员"),
    USER("USER", "普通用户"),
    STUDENT("STUDENT", "学生"),
    TEACHER("TEACHER", "教师");

    private final String code;
    private final String description;

    RoleType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}

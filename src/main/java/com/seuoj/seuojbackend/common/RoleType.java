package com.seuoj.seuojbackend.common;

import java.util.Collection;
import lombok.Getter;

/**
 * 角色类型枚举
 */
@Getter
public enum RoleType {
    USER("USER", "普通用户"),
    TEACHER("TEACHER", "教师"),
    ADMIN("ADMIN", "管理员"),
    SUPER_ADMIN("SUPER_ADMIN", "超管");

    private final String code;
    private final String description;

    RoleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static boolean hasAdminRole(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleCodes.contains(ADMIN.getCode()) || roleCodes.contains(SUPER_ADMIN.getCode());
    }

    /**
     * 判断是否包含教师角色
     */
    public static boolean hasTeacherRole(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleCodes.contains(TEACHER.getCode());
    }

    /**
     * 判断是否包含教师或管理员角色
     */
    public static boolean hasTeacherOrAdminRole(Collection<String> roleCodes) {
        return hasTeacherRole(roleCodes) || hasAdminRole(roleCodes);
    }
}

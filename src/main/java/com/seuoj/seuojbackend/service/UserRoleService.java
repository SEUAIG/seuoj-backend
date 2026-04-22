package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserRoleService {

    private final UserRoleRelMapper userRoleRelMapper;

    public UserRoleService(UserRoleRelMapper userRoleRelMapper) {
        this.userRoleRelMapper = userRoleRelMapper;
    }

    public List<String> getRoleCodesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> roleCodes = userRoleRelMapper.getRoleCodesByUserId(userId);
        return roleCodes == null ? Collections.emptyList() : roleCodes;
    }

    public boolean isAdmin(Long userId) {
        List<String> roles = getRoleCodesByUserId(userId);
        return roles.contains(RoleType.ADMIN.getCode()) || roles.contains(RoleType.SUPER_ADMIN.getCode());
    }

    public boolean isSuperAdmin(Long userId) {
        return getRoleCodesByUserId(userId).contains(RoleType.SUPER_ADMIN.getCode());
    }

    public boolean isTeacher(Long userId) {
        return getRoleCodesByUserId(userId).contains(RoleType.TEACHER.getCode());
    }

    public boolean isTeacherOrAdmin(Long userId) {
        List<String> roles = getRoleCodesByUserId(userId);
        return roles.contains(RoleType.TEACHER.getCode())
                || roles.contains(RoleType.ADMIN.getCode())
                || roles.contains(RoleType.SUPER_ADMIN.getCode());
    }

    public String getHighestRoleLabel(Long userId) {
        List<String> roleCodes = getRoleCodesByUserId(userId);
        if (roleCodes.contains(RoleType.SUPER_ADMIN.getCode())) {
            return "superadmin";
        }
        if (roleCodes.contains(RoleType.ADMIN.getCode())) {
            return "admin";
        }
        if (roleCodes.contains(RoleType.TEACHER.getCode())) {
            return "teacher";
        }
        return "student";
    }
}

package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 用户角色服务 封装角色查询与常用角色判断逻辑
 */
@Service
public class UserRoleService {

    private final UserRoleRelMapper userRoleRelMapper;

    public UserRoleService(UserRoleRelMapper userRoleRelMapper) {
        this.userRoleRelMapper = userRoleRelMapper;
    }

    /**
     * 根据用户 ID 查询角色编码列表
     *
     * @param userId 用户 ID
     * @return 角色编码列表；当用户不存在角色或 userId 为空时返回空列表
     */
    public List<String> getRoleCodesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> roleCodes = userRoleRelMapper.getRoleCodesByUserId(userId);
        return roleCodes == null ? Collections.emptyList() : roleCodes;
    }

    /**
     * 判断用户是否拥有管理员权限
     *
     * @param userId 用户 ID
     * @return true 表示拥有 ADMIN 或 SUPER_ADMIN 角色
     */
    public boolean isAdmin(Long userId) {
        return RoleType.hasAdminRole(getRoleCodesByUserId(userId));
    }

    /**
     * 判断用户是否包含教师角色
     */
    public boolean isTeacher(Long userId) {
        return RoleType.hasTeacherRole(getRoleCodesByUserId(userId));
    }

    /**
     * 判断用户是否包含教师或管理员角色
     */
    public boolean isTeacherOrAdmin(Long userId) {
        return RoleType.hasTeacherOrAdminRole(getRoleCodesByUserId(userId));
    }

    /**
     * 获取用户的最高权限角色标签 用于登录态返回
     *
     * @param userId 用户 ID
     * @return superadmin / admin / user
     */
    public String getHighestRoleLabel(Long userId) {
        List<String> roleCodes = getRoleCodesByUserId(userId);
        if (roleCodes.contains(RoleType.SUPER_ADMIN.getCode())) {
            return "superadmin";
        }
        if (roleCodes.contains(RoleType.ADMIN.getCode())) {
            return "admin";
        }
        return "user";
    }
}

package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.common.Capability;
import com.seuoj.seuojbackend.mapper.UserCapabilityMapper;
import java.util.EnumSet;
import java.util.Set;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserRoleService {

    private final UserRoleRelMapper userRoleRelMapper;
    private final UserCapabilityMapper capabilities;

    public UserRoleService(UserRoleRelMapper userRoleRelMapper, UserCapabilityMapper capabilities) {
        this.userRoleRelMapper = userRoleRelMapper;
        this.capabilities = capabilities;
    }

    public Set<Capability> getCapabilities(Long userId) {
        if (userId == null) return Set.of();
        List<String> roles = getRoleCodesByUserId(userId);
        if (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")) return EnumSet.allOf(Capability.class);
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        for (String code : capabilities.findByUserId(userId)) {
            Capability c = Capability.valueOf(code);
            if (c != Capability.ACCOUNT_ADMIN && c != Capability.TEACHER) result.add(c);
        }
        if (roles.contains("TEACHER")) {
            result.add(Capability.TEACHER);
            result.add(Capability.PROBLEM_CREATE);
            result.add(Capability.CONTEST_CREATE);
        }
        return result;
    }

    public boolean hasCapability(Long userId, Capability capability) {
        return getCapabilities(userId).contains(capability);
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

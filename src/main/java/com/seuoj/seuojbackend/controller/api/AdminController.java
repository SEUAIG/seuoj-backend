package com.seuoj.seuojbackend.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.service.UserRoleService;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin")
public class AdminController {

    private final UserInfoMapper userInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserRoleService userRoleService;

    public AdminController(UserInfoMapper userInfoMapper,
                           UserRoleMapper userRoleMapper,
                           UserRoleRelMapper userRoleRelMapper,
                           UserRoleService userRoleService) {
        this.userInfoMapper = userInfoMapper;
        this.userRoleMapper = userRoleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.userRoleService = userRoleService;
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PutMapping("/user/{userId}/role")
    public Result<Void> setUserRole(
            @PathVariable("userId") Long targetUserId,
            @RequestBody Map<String, String> body) {
        Long currentUserId = AuthContexts.requiredUserId();
        String targetRoleCode = body.get("role");
        if (targetRoleCode == null || targetRoleCode.isBlank()) {
            throw new BadRequestException("role 不能为空");
        }
        targetRoleCode = targetRoleCode.trim().toUpperCase();

        if (RoleType.SUPER_ADMIN.getCode().equals(targetRoleCode)) {
            throw new ForbiddenException("SUPER_ADMIN 角色仅可通过数据库操作授予");
        }

        if (RoleType.ADMIN.getCode().equals(targetRoleCode) && !userRoleService.isSuperAdmin(currentUserId)) {
            throw new ForbiddenException("仅 SUPER_ADMIN 可授予 ADMIN 角色");
        }

        UserInfo targetUser = userInfoMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new NotFoundException("用户不存在");
        }

        UserRole targetRole = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleCode, targetRoleCode)
                .eq(UserRole::getIsDel, 0));
        if (targetRole == null) {
            throw new BadRequestException("角色不存在: " + targetRoleCode);
        }

        // Soft-delete all existing role associations
        userRoleRelMapper.update(null, new LambdaUpdateWrapper<UserRoleRel>()
                .set(UserRoleRel::getIsDel, 1)
                .eq(UserRoleRel::getUserId, targetUser.getId()));

        // Insert new role
        UserRoleRel rel = new UserRoleRel();
        rel.setUserId(targetUser.getId());
        rel.setRoleId(targetRole.getId());
        userRoleRelMapper.insert(rel);

        return Result.success();
    }
}

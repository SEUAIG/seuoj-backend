package com.seuoj.seuojbackend.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.entity.ResourcePermission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ResourcePermissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.service.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/permission")
public class PermissionController {

    private final PermissionService permissionService;
    private final ResourcePermissionMapper resourcePermissionMapper;
    private final UserInfoMapper userInfoMapper;

    public PermissionController(PermissionService permissionService,
                                ResourcePermissionMapper resourcePermissionMapper,
                                UserInfoMapper userInfoMapper) {
        this.permissionService = permissionService;
        this.resourcePermissionMapper = resourcePermissionMapper;
        this.userInfoMapper = userInfoMapper;
    }

    @PostMapping("/grant")
    public Result<Void> grant(@RequestBody Map<String, String> body) {
        Long userId = AuthContexts.requiredUserId();
        ResourceType type = parseResourceType(body.get("resourceType"));
        Long resourceId = parseLong(body.get("resourceId"));
        Long targetUserId = resolveUserIdByPublicId(body.get("targetUserPublicId"));
        PermissionOp op = parsePermissionOp(body.get("permission"));

        permissionService.grantPermission(userId, type, resourceId, targetUserId, op);
        return Result.success();
    }

    @DeleteMapping("/revoke")
    public Result<Void> revoke(@RequestBody Map<String, String> body) {
        Long userId = AuthContexts.requiredUserId();
        ResourceType type = parseResourceType(body.get("resourceType"));
        Long resourceId = parseLong(body.get("resourceId"));
        Long targetUserId = resolveUserIdByPublicId(body.get("targetUserPublicId"));
        PermissionOp op = parsePermissionOp(body.get("permission"));

        permissionService.revokePermission(userId, type, resourceId, targetUserId, op);
        return Result.success();
    }

    @GetMapping("/{type}/{resourceId}")
    public Result<List<ResourcePermission>> list(
            @PathVariable("type") String type,
            @PathVariable("resourceId") Long resourceId) {
        Long userId = AuthContexts.requiredUserId();
        ResourceType rt = parseResourceType(type);
        permissionService.assertPermission(userId, rt, resourceId, PermissionOp.WRITE);

        List<ResourcePermission> permissions = resourcePermissionMapper.selectList(
                new LambdaQueryWrapper<ResourcePermission>()
                        .eq(ResourcePermission::getResourceType, rt.name())
                        .eq(ResourcePermission::getResourceId, resourceId));
        return Result.success(permissions);
    }

    private ResourceType parseResourceType(String value) {
        try {
            return ResourceType.valueOf(value);
        } catch (Exception e) {
            throw new BadRequestException("无效的 resourceType: " + value);
        }
    }

    private PermissionOp parsePermissionOp(String value) {
        try {
            return PermissionOp.valueOf(value);
        } catch (Exception e) {
            throw new BadRequestException("无效的 permission: " + value);
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            throw new BadRequestException("无效的 ID: " + value);
        }
    }

    private Long resolveUserIdByPublicId(String userPublicId) {
        if (userPublicId == null || userPublicId.isBlank()) {
            throw new BadRequestException("targetUserPublicId 不能为空");
        }
        UserInfo user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getPublicId, userPublicId));
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user.getId();
    }
}

package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.entity.ResourcePermission;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import com.seuoj.seuojbackend.mapper.ResourcePermissionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final ResourcePermissionMapper resourcePermissionMapper;
    private final UserRoleService userRoleService;
    private final ProblemMapper problemMapper;
    private final ProblemSetMapper problemSetMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ContestMapper contestMapper;
    private final ContestRegisterRelMapper contestRegisterRelMapper;

    public PermissionService(ResourcePermissionMapper resourcePermissionMapper,
                             UserRoleService userRoleService,
                             ProblemMapper problemMapper,
                             ProblemSetMapper problemSetMapper,
                             ClassInfoMapper classInfoMapper,
                             ContestMapper contestMapper,
                             ContestRegisterRelMapper contestRegisterRelMapper) {
        this.resourcePermissionMapper = resourcePermissionMapper;
        this.userRoleService = userRoleService;
        this.problemMapper = problemMapper;
        this.problemSetMapper = problemSetMapper;
        this.classInfoMapper = classInfoMapper;
        this.contestMapper = contestMapper;
        this.contestRegisterRelMapper = contestRegisterRelMapper;
    }

    /**
     * Permission resolution chain:
     * 1. ADMIN/SUPER_ADMIN -> READ+WRITE all
     * 2. Explicit grant in resource_permission (WRITE implies READ)
     * 3. is_public == true && op == READ -> pass
     * 4. Inherited access (class membership -> assignment/contest)
     * 5. TEACHER role -> READ all + can create
     * 6. Deny
     */
    public boolean hasPermission(Long userId, ResourceType type, Long resourceId, PermissionOp op) {
        if (userId == null) {
            return false;
        }

        List<String> roles = userRoleService.getRoleCodesByUserId(userId);

        // 1. ADMIN/SUPER_ADMIN
        if (roles.contains(RoleType.ADMIN.getCode()) || roles.contains(RoleType.SUPER_ADMIN.getCode())) {
            return true;
        }

        // 2. Explicit grant
        if (op == PermissionOp.WRITE) {
            if (resourcePermissionMapper.hasPermission(type.name(), resourceId, userId, PermissionOp.WRITE.name())) {
                return true;
            }
        } else {
            if (resourcePermissionMapper.hasAnyPermission(type.name(), resourceId, userId)) {
                return true;
            }
        }

        // 3. Public resource + READ
        if (op == PermissionOp.READ && isResourcePublic(type, resourceId)) {
            return true;
        }

        // 4. Inherited access
        if (op == PermissionOp.READ && hasInheritedAccess(userId, type, resourceId)) {
            return true;
        }

        // 5. TEACHER default: READ all
        if (op == PermissionOp.READ && roles.contains(RoleType.TEACHER.getCode())) {
            return true;
        }

        // 6. Deny
        return false;
    }

    public void assertPermission(Long userId, ResourceType type, Long resourceId, PermissionOp op) {
        if (!hasPermission(userId, type, resourceId, op)) {
            throw new ForbiddenException("无权操作");
        }
    }

    public boolean canCreate(Long userId, ResourceType type) {
        if (userId == null) {
            return false;
        }
        List<String> roles = userRoleService.getRoleCodesByUserId(userId);
        if (roles.contains(RoleType.ADMIN.getCode()) || roles.contains(RoleType.SUPER_ADMIN.getCode())) {
            return true;
        }
        if (roles.contains(RoleType.TEACHER.getCode())) {
            return true;
        }
        // STUDENT can create PROBLEM_SET only
        if (type == ResourceType.PROBLEM_SET) {
            return true;
        }
        return false;
    }

    public void assertCanCreate(Long userId, ResourceType type) {
        if (!canCreate(userId, type)) {
            throw new ForbiddenException("无权创建");
        }
    }

    public void grantPermission(Long granterId, ResourceType type, Long resourceId,
                                Long targetUserId, PermissionOp op) {
        assertPermission(granterId, type, resourceId, PermissionOp.WRITE);

        ResourcePermission perm = new ResourcePermission();
        perm.setResourceType(type.name());
        perm.setResourceId(resourceId);
        perm.setUserId(targetUserId);
        perm.setPermission(op.name());
        perm.setGrantedBy(granterId);
        resourcePermissionMapper.insert(perm);
    }

    public void revokePermission(Long revokerId, ResourceType type, Long resourceId,
                                 Long targetUserId, PermissionOp op) {
        assertPermission(revokerId, type, resourceId, PermissionOp.WRITE);

        resourcePermissionMapper.delete(new LambdaQueryWrapper<ResourcePermission>()
                .eq(ResourcePermission::getResourceType, type.name())
                .eq(ResourcePermission::getResourceId, resourceId)
                .eq(ResourcePermission::getUserId, targetUserId)
                .eq(ResourcePermission::getPermission, op.name()));
    }

    public void autoGrantCreator(ResourceType type, Long resourceId, Long creatorUserId) {
        ResourcePermission perm = new ResourcePermission();
        perm.setResourceType(type.name());
        perm.setResourceId(resourceId);
        perm.setUserId(creatorUserId);
        perm.setPermission(PermissionOp.WRITE.name());
        perm.setGrantedBy(creatorUserId);
        resourcePermissionMapper.insert(perm);
    }

    private boolean isResourcePublic(ResourceType type, Long resourceId) {
        return switch (type) {
            case PROBLEM -> {
                Problem p = problemMapper.selectById(resourceId);
                yield p != null && Boolean.TRUE.equals(p.getIsPublic());
            }
            case PROBLEM_SET -> {
                ProblemSet ps = problemSetMapper.selectById(resourceId);
                yield ps != null && Boolean.TRUE.equals(ps.getIsPublic());
            }
            case CLASS -> {
                ClassInfo c = classInfoMapper.selectById(resourceId);
                yield c != null && Boolean.TRUE.equals(c.getIsPublic());
            }
            case CONTEST -> {
                Contest ct = contestMapper.selectById(resourceId);
                yield ct != null && Boolean.TRUE.equals(ct.getIsPublic());
            }
        };
    }

    private boolean hasInheritedAccess(Long userId, ResourceType type, Long resourceId) {
        return switch (type) {
            case PROBLEM_SET -> resourcePermissionMapper.hasProblemSetAccessViaAssignment(resourceId, userId);
            case CONTEST -> hasContestInheritedAccess(userId, resourceId);
            case PROBLEM, CLASS -> false;
        };
    }

    private boolean hasContestInheritedAccess(Long userId, Long contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isRegistered = contestRegisterRelMapper.selectCount(
                new LambdaQueryWrapper<ContestRegisterRel>()
                        .eq(ContestRegisterRel::getContestId, contestId)
                        .eq(ContestRegisterRel::getUserId, userId)) > 0;
        boolean isClassMember = resourcePermissionMapper.hasContestAccessViaClass(contestId, userId);

        if (now.isBefore(contest.getStartTime())) {
            return false;
        }
        if (!now.isAfter(contest.getEndTime())) {
            return isRegistered;
        }
        // After contest
        return isRegistered || isClassMember || Boolean.TRUE.equals(contest.getIsPublic());
    }
}

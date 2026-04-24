package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.AssignmentProblemRel;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.entity.ProblemSetProblemRel;
import com.seuoj.seuojbackend.entity.ResourcePermission;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.AssignmentProblemRelMapper;
import org.springframework.dao.DuplicateKeyException;import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestProblemRelMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetProblemRelMapper;
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
    private final ProblemSetProblemRelMapper problemSetProblemRelMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ContestMapper contestMapper;
    private final ContestProblemRelMapper contestProblemRelMapper;
    private final ContestRegisterRelMapper contestRegisterRelMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemRelMapper assignmentProblemRelMapper;

    public PermissionService(ResourcePermissionMapper resourcePermissionMapper,
                             UserRoleService userRoleService,
                             ProblemMapper problemMapper,
                             ProblemSetMapper problemSetMapper,
                             ProblemSetProblemRelMapper problemSetProblemRelMapper,
                             ClassInfoMapper classInfoMapper,
                             ContestMapper contestMapper,
                             ContestProblemRelMapper contestProblemRelMapper,
                             ContestRegisterRelMapper contestRegisterRelMapper,
                             AssignmentMapper assignmentMapper,
                             AssignmentProblemRelMapper assignmentProblemRelMapper) {
        this.resourcePermissionMapper = resourcePermissionMapper;
        this.userRoleService = userRoleService;
        this.problemMapper = problemMapper;
        this.problemSetMapper = problemSetMapper;
        this.problemSetProblemRelMapper = problemSetProblemRelMapper;
        this.classInfoMapper = classInfoMapper;
        this.contestMapper = contestMapper;
        this.contestProblemRelMapper = contestProblemRelMapper;
        this.contestRegisterRelMapper = contestRegisterRelMapper;
        this.assignmentMapper = assignmentMapper;
        this.assignmentProblemRelMapper = assignmentProblemRelMapper;
    }

    /**
     * Permission resolution chain:
     * 1. is_public == true && op == READ -> pass (anonymous allowed)
     * 2. ADMIN/SUPER_ADMIN -> READ+WRITE all
     * 3. Explicit grant in resource_permission (WRITE implies READ)
     * 4. Inherited access (class membership -> assignment/contest)
     * 5. TEACHER role -> READ all + can create
     * 6. Deny
     */
    public boolean hasPermission(Long userId, ResourceType type, Long resourceId, PermissionOp op) {
        // 1. Public resource + READ (anonymous allowed)
        if (op == PermissionOp.READ && isResourcePublic(type, resourceId)) {
            return true;
        }

        if (userId == null) {
            return false;
        }

        List<String> roles = userRoleService.getRoleCodesByUserId(userId);

        // 2. ADMIN/SUPER_ADMIN
        if (roles.contains(RoleType.ADMIN.getCode()) || roles.contains(RoleType.SUPER_ADMIN.getCode())) {
            return true;
        }

        // 3. Explicit grant
        if (op == PermissionOp.WRITE) {
            if (resourcePermissionMapper.hasPermission(type.name(), resourceId, userId, PermissionOp.WRITE.name())) {
                return true;
            }
        } else {
            if (resourcePermissionMapper.hasAnyPermission(type.name(), resourceId, userId)) {
                return true;
            }
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
        try {
            resourcePermissionMapper.insert(perm);
        } catch (DuplicateKeyException ignored) {
        }
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

    public void assertProblemAccessViaAssignment(Long userId, Long problemId, Long assignmentId) {
        if (userId != null && userRoleService.isTeacherOrAdmin(userId)) {
            return;
        }

        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new NotFoundException("作业不存在");
        }

        if (!"PUBLISHED".equals(assignment.getStatus())) {
            throw new ForbiddenException("无权操作");
        }
        LocalDateTime now = LocalDateTime.now();
        if (assignment.getVisibleFrom() != null && now.isBefore(assignment.getVisibleFrom())) {
            throw new ForbiddenException("无权操作");
        }

        assertPermission(userId, ResourceType.CLASS, assignment.getClassId(), PermissionOp.READ);

        Long count = assignmentProblemRelMapper.selectCount(
                new LambdaQueryWrapper<AssignmentProblemRel>()
                        .eq(AssignmentProblemRel::getAssignmentId, assignmentId)
                        .eq(AssignmentProblemRel::getProblemId, problemId));
        if (count == null || count == 0) {
            throw new NotFoundException("题目不在该作业中");
        }
    }

    public void assertProblemAccessViaContest(Long userId, Long problemId, Long contestId) {
        if (userId != null && userRoleService.isTeacherOrAdmin(userId)) {
            return;
        }

        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }

        assertPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.READ);

        Long count = contestProblemRelMapper.selectCount(
                new LambdaQueryWrapper<ContestProblemRel>()
                        .eq(ContestProblemRel::getContestId, contestId)
                        .eq(ContestProblemRel::getProblemId, problemId));
        if (count == null || count == 0) {
            throw new NotFoundException("题目不在该比赛中");
        }
    }

    public void assertProblemAccessViaProblemSet(Long userId, Long problemId, Long problemSetId) {
        if (userId != null && userRoleService.isTeacherOrAdmin(userId)) {
            return;
        }

        ProblemSet problemSet = problemSetMapper.selectById(problemSetId);
        if (problemSet == null) {
            throw new NotFoundException("题单不存在");
        }

        assertPermission(userId, ResourceType.PROBLEM_SET, problemSetId, PermissionOp.READ);

        Long count = problemSetProblemRelMapper.selectCount(
                new LambdaQueryWrapper<ProblemSetProblemRel>()
                        .eq(ProblemSetProblemRel::getProblemSetId, problemSetId)
                        .eq(ProblemSetProblemRel::getProblemId, problemId));
        if (count == null || count == 0) {
            throw new NotFoundException("题目不在该题单中");
        }
    }
}

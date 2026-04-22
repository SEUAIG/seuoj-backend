package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.assignment.AssignmentCreateDTO;
import com.seuoj.seuojbackend.dto.assignment.AssignmentUpdateDTO;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssignmentService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CLOSED = "CLOSED";

    private final AssignmentMapper assignmentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ProblemSetMapper problemSetMapper;
    private final PermissionService permissionService;

    public AssignmentService(AssignmentMapper assignmentMapper,
                             ClassInfoMapper classInfoMapper,
                             ProblemSetMapper problemSetMapper,
                             PermissionService permissionService) {
        this.assignmentMapper = assignmentMapper;
        this.classInfoMapper = classInfoMapper;
        this.problemSetMapper = problemSetMapper;
        this.permissionService = permissionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public String createAssignment(String classPublicId, AssignmentCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        ProblemSet problemSet = getProblemSetByPublicId(dto.getProblemSetPublicId());

        Assignment assignment = new Assignment();
        assignment.setPublicId(UUID.randomUUID().toString());
        assignment.setClassId(classInfo.getId());
        assignment.setProblemSetId(problemSet.getId());
        assignment.setTitle(normalizeRequired(dto.getTitle(), "title 不能为空"));
        assignment.setDescription(dto.getDescription());
        assignment.setStatus(STATUS_DRAFT);
        assignment.setDeadline(dto.getDeadline());
        assignment.setCreatedByUserId(userId);
        assignmentMapper.insert(assignment);

        return assignment.getPublicId();
    }

    public IPage<Assignment> getAssignmentPage(String classPublicId, Integer current, Integer size) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        boolean canWrite = permissionService.hasPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        LambdaQueryWrapper<Assignment> wrapper = new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getClassId, classInfo.getId())
                .orderByDesc(Assignment::getCreatedAt);

        if (!canWrite) {
            wrapper.eq(Assignment::getStatus, STATUS_PUBLISHED);
        }

        return assignmentMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Assignment getAssignmentDetail(String classPublicId, String assignmentPublicId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        Assignment assignment = getAssignmentByPublicId(assignmentPublicId);
        if (!assignment.getClassId().equals(classInfo.getId())) {
            throw new NotFoundException("作业不存在");
        }

        boolean canWrite = permissionService.hasPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);
        if (!canWrite && !STATUS_PUBLISHED.equals(assignment.getStatus())) {
            throw new NotFoundException("作业不存在");
        }

        return assignment;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAssignment(String classPublicId, String assignmentPublicId, AssignmentUpdateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = getAssignmentByPublicId(assignmentPublicId);
        if (!assignment.getClassId().equals(classInfo.getId())) {
            throw new NotFoundException("作业不存在");
        }

        Assignment update = new Assignment();
        update.setId(assignment.getId());

        boolean changed = false;
        if (dto.getTitle() != null) {
            update.setTitle(normalizeRequired(dto.getTitle(), "title 不能为空"));
            changed = true;
        }
        if (dto.getDescription() != null) {
            update.setDescription(dto.getDescription());
            changed = true;
        }
        if (dto.getDeadline() != null) {
            update.setDeadline(dto.getDeadline());
            changed = true;
        }
        if (dto.getStatus() != null) {
            validateStatusTransition(assignment.getStatus(), dto.getStatus());
            update.setStatus(dto.getStatus());
            changed = true;
        }

        if (changed) {
            assignmentMapper.updateById(update);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAssignment(String classPublicId, String assignmentPublicId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = getAssignmentByPublicId(assignmentPublicId);
        if (!assignment.getClassId().equals(classInfo.getId())) {
            throw new NotFoundException("作业不存在");
        }

        assignmentMapper.deleteById(assignment.getId());
    }

    private void validateStatusTransition(String current, String target) {
        List<String> allowed = switch (current) {
            case STATUS_DRAFT -> List.of(STATUS_PUBLISHED);
            case STATUS_PUBLISHED -> List.of(STATUS_CLOSED);
            case STATUS_CLOSED -> Collections.emptyList();
            default -> Collections.emptyList();
        };

        if (!allowed.contains(target)) {
            throw new BadRequestException("不允许的状态变更: " + current + " → " + target);
        }
    }

    private ClassInfo getClassByPublicId(String classPublicId) {
        if (!StringUtils.hasText(classPublicId)) {
            throw new BadRequestException("class_public_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectOne(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getPublicId, classPublicId));
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        return classInfo;
    }

    private ProblemSet getProblemSetByPublicId(String problemSetPublicId) {
        if (!StringUtils.hasText(problemSetPublicId)) {
            throw new BadRequestException("problem_set_public_id 不能为空");
        }
        ProblemSet problemSet = problemSetMapper.selectOne(new LambdaQueryWrapper<ProblemSet>()
                .eq(ProblemSet::getPublicId, problemSetPublicId));
        if (problemSet == null) {
            throw new NotFoundException("题单不存在");
        }
        return problemSet;
    }

    private Assignment getAssignmentByPublicId(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BadRequestException("assignment_public_id 不能为空");
        }
        Assignment assignment = assignmentMapper.selectOne(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getPublicId, publicId));
        if (assignment == null) {
            throw new NotFoundException("作业不存在");
        }
        return assignment;
    }

    private String normalizeRequired(String raw, String message) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }
}

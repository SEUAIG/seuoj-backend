package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.announcement.AttachmentDTO;
import com.seuoj.seuojbackend.dto.assignment.AssignmentCreateDTO;
import com.seuoj.seuojbackend.dto.assignment.AssignmentImportFromProblemSetDTO;
import com.seuoj.seuojbackend.dto.assignment.AssignmentProblemEditDTO;
import com.seuoj.seuojbackend.dto.assignment.AssignmentUpdateDTO;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.AssignmentIntroAttachment;
import com.seuoj.seuojbackend.entity.AssignmentProblemRel;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemSetProblemRel;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.AssignmentIntroAttachmentMapper;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.AssignmentProblemRelMapper;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ClassStudentRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetProblemRelMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.entity.ClassStudentRel;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.vo.assignment.AssignmentDetailVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionListItemVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionPageVO;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssignmentService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final AssignmentMapper assignmentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final AssignmentProblemRelMapper assignmentProblemRelMapper;
    private final AssignmentIntroAttachmentMapper introAttachmentMapper;
    private final ClassStudentRelMapper classStudentRelMapper;
    private final ProblemMapper problemMapper;
    private final ProblemSetMapper problemSetMapper;
    private final ProblemSetProblemRelMapper problemSetProblemRelMapper;
    private final SubmissionMapper submissionMapper;
    private final PermissionService permissionService;

    public AssignmentService(AssignmentMapper assignmentMapper,
                             ClassInfoMapper classInfoMapper,
                             AssignmentProblemRelMapper assignmentProblemRelMapper,
                             AssignmentIntroAttachmentMapper introAttachmentMapper,
                             ClassStudentRelMapper classStudentRelMapper,
                             ProblemMapper problemMapper,
                             ProblemSetMapper problemSetMapper,
                             ProblemSetProblemRelMapper problemSetProblemRelMapper,
                             SubmissionMapper submissionMapper,
                             PermissionService permissionService) {
        this.assignmentMapper = assignmentMapper;
        this.classInfoMapper = classInfoMapper;
        this.assignmentProblemRelMapper = assignmentProblemRelMapper;
        this.introAttachmentMapper = introAttachmentMapper;
        this.classStudentRelMapper = classStudentRelMapper;
        this.problemMapper = problemMapper;
        this.problemSetMapper = problemSetMapper;
        this.problemSetProblemRelMapper = problemSetProblemRelMapper;
        this.submissionMapper = submissionMapper;
        this.permissionService = permissionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createAssignment(Long classId, AssignmentCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = requireClass(classId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = new Assignment();
        assignment.setClassId(classInfo.getId());
        assignment.setTitle(normalizeRequired(dto.getTitle(), "title 不能为空"));
        assignment.setDescription(dto.getDescription());
        assignment.setIntroduction(dto.getIntroduction());
        assignment.setStatus(STATUS_DRAFT);
        assignment.setDeadline(dto.getDeadline());
        assignment.setVisibleFrom(dto.getVisibleFrom());
        assignment.setVisibleTo(dto.getVisibleTo());
        assignment.setCreatedByUserId(userId);
        assignmentMapper.insert(assignment);

        if (dto.getProblemIds() != null && !dto.getProblemIds().isEmpty()) {
            insertProblems(assignment.getId(), dto.getProblemIds());
        }

        if (dto.getIntroAttachments() != null) {
            for (AttachmentDTO att : dto.getIntroAttachments()) {
                insertIntroAttachment(assignment.getId(), att);
            }
        }

        return assignment.getId();
    }

    public IPage<Assignment> getAssignmentPage(Long classId, Integer current, Integer size) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = requireClass(classId);
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

    public Assignment getAssignmentDetail(Long classId, Long assignmentId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = requireClass(classId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        Assignment assignment = requireAssignment(assignmentId, classInfo.getId());

        boolean canWrite = permissionService.hasPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);
        if (!canWrite) {
            if (!STATUS_PUBLISHED.equals(assignment.getStatus())) {
                throw new NotFoundException("作业不存在");
            }
            LocalDateTime now = LocalDateTime.now();
            if (assignment.getVisibleFrom() != null && now.isBefore(assignment.getVisibleFrom())) {
                throw new BadRequestException("作业尚未开放");
            }
            if (assignment.getVisibleTo() != null && now.isAfter(assignment.getVisibleTo())) {
                throw new BadRequestException("作业已关闭");
            }
        }

        return assignment;
    }

    public AssignmentDetailVO getAssignmentDetailVO(Long classId, Long assignmentId) {
        Long userId = AuthContexts.requiredUserId();
        Assignment assignment = getAssignmentDetail(classId, assignmentId);
        ClassInfo classInfo = requireClass(classId);
        boolean canWrite = permissionService.hasPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        List<AssignmentDetailVO.ProblemItem> problems = buildProblemList(assignment.getId());

        Long memberCount = classStudentRelMapper.selectCount(
                new LambdaQueryWrapper<ClassStudentRel>()
                        .eq(ClassStudentRel::getClassId, classId));

        List<AssignmentIntroAttachment> attachments = introAttachmentMapper.selectList(
                new LambdaQueryWrapper<AssignmentIntroAttachment>()
                        .eq(AssignmentIntroAttachment::getAssignmentId, assignment.getId())
                        .eq(AssignmentIntroAttachment::getIsDel, 0)
                        .orderByAsc(AssignmentIntroAttachment::getCreatedAt));

        AssignmentDetailVO vo = new AssignmentDetailVO();
        vo.setAssignmentId(assignment.getId());
        vo.setClassId(assignment.getClassId());
        vo.setTitle(assignment.getTitle());
        vo.setDescription(assignment.getDescription());
        vo.setIntroduction(assignment.getIntroduction());
        vo.setStatus(assignment.getStatus());
        vo.setDeadline(assignment.getDeadline());
        vo.setVisibleFrom(assignment.getVisibleFrom());
        vo.setVisibleTo(assignment.getVisibleTo());
        vo.setProblemCount(problems.size());
        vo.setMemberCount(memberCount != null ? memberCount.intValue() : 0);
        vo.setProblems(problems);
        vo.setIntroAttachments(attachments.stream().map(att -> {
            AssignmentDetailVO.IntroAttachmentItem item = new AssignmentDetailVO.IntroAttachmentItem();
            item.setId(att.getId());
            item.setFilePath(att.getFilePath());
            item.setFileName(att.getFileName());
            item.setFileSize(att.getFileSize());
            return item;
        }).toList());
        vo.setCanWrite(canWrite);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAssignment(Long classId, Long assignmentId, AssignmentUpdateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = requireClass(classId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = requireAssignment(assignmentId, classInfo.getId());

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
        if (dto.getIntroduction() != null) {
            update.setIntroduction(dto.getIntroduction());
            changed = true;
        }
        if (dto.getDeadline() != null) {
            update.setDeadline(dto.getDeadline());
            changed = true;
        }
        if (dto.getVisibleFrom() != null) {
            update.setVisibleFrom(dto.getVisibleFrom());
            changed = true;
        }
        if (dto.getVisibleTo() != null) {
            update.setVisibleTo(dto.getVisibleTo());
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

        if (dto.getRemoveIntroAttachmentIds() != null && !dto.getRemoveIntroAttachmentIds().isEmpty()) {
            for (Long attachId : dto.getRemoveIntroAttachmentIds()) {
                AssignmentIntroAttachment att = introAttachmentMapper.selectById(attachId);
                if (att != null && att.getAssignmentId().equals(assignment.getId())) {
                    introAttachmentMapper.deleteById(attachId);
                }
            }
        }
        if (dto.getAddIntroAttachments() != null) {
            for (AttachmentDTO att : dto.getAddIntroAttachments()) {
                insertIntroAttachment(assignment.getId(), att);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAssignment(Long classId, Long assignmentId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = requireClass(classId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = requireAssignment(assignmentId, classInfo.getId());
        assignmentMapper.deleteById(assignment.getId());
    }

    public List<AssignmentDetailVO.ProblemItem> getAssignmentProblems(Long classId, Long assignmentId) {
        Assignment assignment = getAssignmentDetail(classId, assignmentId);
        return buildProblemList(assignment.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceAssignmentProblems(Long classId, Long assignmentId, AssignmentProblemEditDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = requireClass(classId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = requireAssignment(assignmentId, classInfo.getId());

        assignmentProblemRelMapper.update(null, new LambdaUpdateWrapper<AssignmentProblemRel>()
                .set(AssignmentProblemRel::getIsDel, 1)
                .eq(AssignmentProblemRel::getAssignmentId, assignment.getId()));

        if (dto.getProblems() != null) {
            int sortOrder = 1;
            for (AssignmentProblemEditDTO.ProblemItem item : dto.getProblems()) {
                Problem problem = problemMapper.selectById(item.getProblemId());
                if (problem == null) {
                    throw new NotFoundException("题目 " + item.getProblemId() + " 不存在");
                }
                AssignmentProblemRel rel = new AssignmentProblemRel();
                rel.setAssignmentId(assignment.getId());
                rel.setProblemId(item.getProblemId());
                rel.setSortOrder(sortOrder++);
                rel.setWeight(item.getWeight() != null ? item.getWeight() : 1);
                assignmentProblemRelMapper.insert(rel);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void importFromProblemSet(Long classId, Long assignmentId, AssignmentImportFromProblemSetDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = requireClass(classId);
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = requireAssignment(assignmentId, classInfo.getId());

        ProblemSet problemSet = problemSetMapper.selectById(dto.getProblemSetId());
        if (problemSet == null) {
            throw new NotFoundException("题单不存在");
        }

        List<ProblemSetProblemRel> psRels = problemSetProblemRelMapper.selectList(
                new LambdaQueryWrapper<ProblemSetProblemRel>()
                        .eq(ProblemSetProblemRel::getProblemSetId, problemSet.getId())
                        .orderByAsc(ProblemSetProblemRel::getSortOrder));

        Set<Long> sourceProblemIds = psRels.stream()
                .map(ProblemSetProblemRel::getProblemId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> activeProblemIds = sourceProblemIds.isEmpty()
                ? Collections.emptySet()
                : problemMapper.selectBatchIds(sourceProblemIds).stream()
                .map(Problem::getId)
                .collect(Collectors.toCollection(HashSet::new));

        Long maxSort = assignmentProblemRelMapper.selectCount(
                new LambdaQueryWrapper<AssignmentProblemRel>()
                        .eq(AssignmentProblemRel::getAssignmentId, assignment.getId()));
        int sortOrder = maxSort != null ? maxSort.intValue() + 1 : 1;

        for (ProblemSetProblemRel psRel : psRels) {
            if (!activeProblemIds.contains(psRel.getProblemId())) {
                continue;
            }
            Long existCount = assignmentProblemRelMapper.selectCount(
                    new LambdaQueryWrapper<AssignmentProblemRel>()
                            .eq(AssignmentProblemRel::getAssignmentId, assignment.getId())
                            .eq(AssignmentProblemRel::getProblemId, psRel.getProblemId()));
            if (existCount != null && existCount > 0) {
                continue;
            }
            AssignmentProblemRel rel = new AssignmentProblemRel();
            rel.setAssignmentId(assignment.getId());
            rel.setProblemId(psRel.getProblemId());
            rel.setSortOrder(sortOrder++);
            rel.setWeight(1);
            assignmentProblemRelMapper.insert(rel);
        }
    }

    public SubmissionPageVO getAssignmentSubmissionPage(Long classId, Long assignmentId,
                                                         Integer current, Integer size) {
        Assignment assignment = getAssignmentDetail(classId, assignmentId);

        List<AssignmentProblemRel> rels = assignmentProblemRelMapper.selectList(
                new LambdaQueryWrapper<AssignmentProblemRel>()
                        .eq(AssignmentProblemRel::getAssignmentId, assignment.getId()));
        List<Long> problemIds = rels.stream().map(AssignmentProblemRel::getProblemId).toList();

        SubmissionPageVO result = new SubmissionPageVO();
        if (problemIds.isEmpty()) {
            result.setCurrent(current);
            result.setSize(size);
            result.setTotal(0);
            result.setRecords(Collections.emptyList());
            return result;
        }

        Page<SubmissionListItemVO> page = new Page<>(current, size);
        IPage<SubmissionListItemVO> pageResult = submissionMapper.selectAssignmentSubmissionPage(
                page, problemIds, classId);

        result.setCurrent(pageResult.getCurrent());
        result.setSize(pageResult.getSize());
        result.setTotal(pageResult.getTotal());
        result.setRecords(pageResult.getRecords());
        return result;
    }

    boolean isVisibleNow(Assignment assignment) {
        LocalDateTime now = LocalDateTime.now();
        if (assignment.getVisibleFrom() != null && now.isBefore(assignment.getVisibleFrom())) {
            return false;
        }
        if (assignment.getVisibleTo() != null && now.isAfter(assignment.getVisibleTo())) {
            return false;
        }
        return true;
    }

    private List<AssignmentDetailVO.ProblemItem> buildProblemList(Long assignmentId) {
        List<AssignmentProblemRel> rels = assignmentProblemRelMapper.selectList(
                new LambdaQueryWrapper<AssignmentProblemRel>()
                        .eq(AssignmentProblemRel::getAssignmentId, assignmentId)
                        .orderByAsc(AssignmentProblemRel::getSortOrder));

        if (rels.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> problemIds = rels.stream().map(AssignmentProblemRel::getProblemId).toList();
        Map<Long, Problem> problemMap = problemMapper.selectBatchIds(problemIds)
                .stream()
                .collect(Collectors.toMap(Problem::getId, p -> p, (a, b) -> a));

        return rels.stream()
                .filter(rel -> problemMap.containsKey(rel.getProblemId()))
                .map(rel -> {
                    Problem problem = problemMap.get(rel.getProblemId());
                    AssignmentDetailVO.ProblemItem item = new AssignmentDetailVO.ProblemItem();
                    item.setProblemId(rel.getProblemId());
                    item.setPid(problem.getPid());
                    item.setTitle(problem.getTitle());
                    item.setSortOrder(rel.getSortOrder());
                    item.setWeight(rel.getWeight());
                    return item;
                })
                .toList();
    }

    private void insertProblems(Long assignmentId, List<Long> problemIds) {
        int sortOrder = 1;
        for (Long problemId : problemIds) {
            Problem problem = problemMapper.selectById(problemId);
            if (problem == null) {
                throw new NotFoundException("题目 " + problemId + " 不存在");
            }
            AssignmentProblemRel rel = new AssignmentProblemRel();
            rel.setAssignmentId(assignmentId);
            rel.setProblemId(problemId);
            rel.setSortOrder(sortOrder++);
            rel.setWeight(1);
            assignmentProblemRelMapper.insert(rel);
        }
    }

    private void insertIntroAttachment(Long assignmentId, AttachmentDTO att) {
        AssignmentIntroAttachment entity = new AssignmentIntroAttachment();
        entity.setAssignmentId(assignmentId);
        entity.setFilePath(att.getFilePath());
        entity.setFileName(att.getFileName());
        entity.setFileSize(att.getFileSize());
        introAttachmentMapper.insert(entity);
    }

    private ClassInfo requireClass(Long classId) {
        if (classId == null) {
            throw new BadRequestException("class_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        return classInfo;
    }

    private Assignment requireAssignment(Long assignmentId, Long classId) {
        if (assignmentId == null) {
            throw new BadRequestException("assignment_id 不能为空");
        }
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new NotFoundException("作业不存在");
        }
        if (!assignment.getClassId().equals(classId)) {
            throw new NotFoundException("作业不存在");
        }
        return assignment;
    }

    private void validateStatusTransition(String current, String target) {
        if (!STATUS_DRAFT.equals(target) && !STATUS_PUBLISHED.equals(target)) {
            throw new BadRequestException("不允许的状态值: " + target);
        }
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

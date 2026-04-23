package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.classinfo.ClassCreateDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassUpdateDTO;
import com.seuoj.seuojbackend.entity.ClassContestRel;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.ClassStudentRel;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ClassContestRelMapper;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ClassStudentRelMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.vo.classinfo.AssignmentOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassCreateVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassPageVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageItemVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageVO;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClassService {

    private final ClassInfoMapper classInfoMapper;
    private final ClassStudentRelMapper classStudentRelMapper;
    private final ClassContestRelMapper classContestRelMapper;
    private final ContestMapper contestMapper;
    private final AssignmentMapper assignmentMapper;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;

    public ClassService(ClassInfoMapper classInfoMapper,
                        ClassStudentRelMapper classStudentRelMapper,
                        ClassContestRelMapper classContestRelMapper,
                        ContestMapper contestMapper,
                        AssignmentMapper assignmentMapper,
                        PermissionService permissionService,
                        UserRoleService userRoleService) {
        this.classInfoMapper = classInfoMapper;
        this.classStudentRelMapper = classStudentRelMapper;
        this.classContestRelMapper = classContestRelMapper;
        this.contestMapper = contestMapper;
        this.assignmentMapper = assignmentMapper;
        this.permissionService = permissionService;
        this.userRoleService = userRoleService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassCreateVO createClass(ClassCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertCanCreate(userId, ResourceType.CLASS);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName(normalizeRequiredText(dto.getName(), "name 不能为空"));
        classInfo.setDescription(dto.getDescription());
        classInfo.setIsPublic(Boolean.TRUE.equals(dto.getIsPublic()));
        classInfo.setCreatedByUserId(userId);
        classInfoMapper.insert(classInfo);

        permissionService.autoGrantCreator(ResourceType.CLASS, classInfo.getId(), userId);

        ClassCreateVO vo = new ClassCreateVO();
        vo.setClassId(classInfo.getId());
        return vo;
    }

    public ClassPageVO getClassPage(Integer current, Integer size) {
        validatePageParam(current, size);

        Long userId = AuthContexts.userIdOrNull();
        boolean isAdmin = userId != null && userRoleService.isAdmin(userId);

        IPage<ClassItemVO> pageResult = classInfoMapper.selectClassPage(new Page<>(current, size), userId, isAdmin);
        ClassPageVO vo = new ClassPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(pageResult.getRecords() == null ? Collections.emptyList() : pageResult.getRecords());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassItemVO updateClass(Long classId, ClassUpdateDTO dto) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        ClassInfo update = new ClassInfo();
        update.setId(classInfo.getId());

        boolean changed = false;
        if (dto.getName() != null) {
            update.setName(normalizeRequiredText(dto.getName(), "name 不能为空"));
            changed = true;
        }
        if (dto.getDescription() != null) {
            update.setDescription(dto.getDescription());
            changed = true;
        }
        if (dto.getIsPublic() != null) {
            update.setIsPublic(dto.getIsPublic());
            changed = true;
        }

        if (changed) {
            classInfoMapper.updateById(update);
        }

        ClassInfo latest = classInfoMapper.selectById(classInfo.getId());
        return toClassItemVO(latest == null ? classInfo : latest);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteClass(Long classId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        classInfoMapper.deleteById(classInfo.getId());

        classStudentRelMapper.update(null, new LambdaUpdateWrapper<ClassStudentRel>()
                .set(ClassStudentRel::getIsDel, 1)
                .eq(ClassStudentRel::getClassId, classInfo.getId()));

        classContestRelMapper.update(null, new LambdaUpdateWrapper<ClassContestRel>()
                .set(ClassContestRel::getIsDel, 1)
                .eq(ClassContestRel::getClassId, classInfo.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void joinClass(Long classId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();

        int restored = classStudentRelMapper.restoreDeletedStudent(classInfo.getId(), userId);
        if (restored > 0) {
            return;
        }

        ClassStudentRel rel = new ClassStudentRel();
        rel.setClassId(classInfo.getId());
        rel.setUserId(userId);
        try {
            classStudentRelMapper.insert(rel);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("已加入该班级");
        }
    }

    public ClassMemberPageVO getClassMemberPage(Long classId, Integer current, Integer size) {
        validatePageParam(current, size);

        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        IPage<ClassMemberItemVO> pageResult = classInfoMapper.selectClassMemberPage(new Page<>(current, size), classInfo.getId());
        ClassMemberPageVO vo = new ClassMemberPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(pageResult.getRecords() == null ? Collections.emptyList() : pageResult.getRecords());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long classId, Long userId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long currentUserId = AuthContexts.requiredUserId();
        permissionService.assertPermission(currentUserId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        int updated = classStudentRelMapper.update(null, new LambdaUpdateWrapper<ClassStudentRel>()
                .set(ClassStudentRel::getIsDel, 1)
                .eq(ClassStudentRel::getClassId, classInfo.getId())
                .eq(ClassStudentRel::getUserId, userId));
        if (updated == 0) {
            throw new NotFoundException("班级成员不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long classId, Long userId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long currentUserId = AuthContexts.requiredUserId();
        permissionService.assertPermission(currentUserId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        int restored = classStudentRelMapper.restoreDeletedStudent(classInfo.getId(), userId);
        if (restored > 0) {
            return;
        }

        ClassStudentRel rel = new ClassStudentRel();
        rel.setClassId(classInfo.getId());
        rel.setUserId(userId);
        try {
            classStudentRelMapper.insert(rel);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("已加入该班级");
        }
    }

    public LinkPageVO getClassContestPage(Long classId, Integer current, Integer size) {
        validatePageParam(current, size);

        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        IPage<LinkPageItemVO> pageResult = classInfoMapper.selectClassContestPage(new Page<>(current, size), classInfo.getId());
        return toLinkPageVO(pageResult);
    }

    @Transactional(rollbackFor = Exception.class)
    public void linkContest(Long classId, Long contestId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }

        int restored = classContestRelMapper.restoreDeletedContestLink(classInfo.getId(), contest.getId());
        if (restored > 0) {
            return;
        }

        ClassContestRel rel = new ClassContestRel();
        rel.setClassId(classInfo.getId());
        rel.setContestId(contest.getId());
        try {
            classContestRelMapper.insert(rel);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("班级与比赛已关联");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlinkContest(Long classId, Long contestId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }

        int updated = classContestRelMapper.update(null, new LambdaUpdateWrapper<ClassContestRel>()
                .set(ClassContestRel::getIsDel, 1)
                .eq(ClassContestRel::getClassId, classInfo.getId())
                .eq(ClassContestRel::getContestId, contest.getId()));
        if (updated == 0) {
            throw new NotFoundException("班级与比赛关联不存在");
        }
    }

    public ClassOverviewVO getClassOverview(Long classId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        Long memberCount = classStudentRelMapper.selectCount(
                new LambdaQueryWrapper<ClassStudentRel>()
                        .eq(ClassStudentRel::getClassId, classInfo.getId()));

        Integer totalProblems = classInfoMapper.selectTotalProblems(classInfo.getId());
        List<ClassOverviewVO.StudentOverviewItem> students = classInfoMapper.selectStudentOverview(classInfo.getId());
        List<ClassOverviewVO.AssignmentProgressItem> assignments = classInfoMapper.selectAssignmentProgress(classInfo.getId());

        ClassOverviewVO vo = new ClassOverviewVO();
        vo.setMemberCount(memberCount != null ? memberCount.intValue() : 0);
        vo.setTotalProblems(totalProblems != null ? totalProblems : 0);
        vo.setStudents(students != null ? students : Collections.emptyList());
        vo.setAssignments(assignments != null ? assignments : Collections.emptyList());
        return vo;
    }

    public AssignmentOverviewVO getAssignmentOverview(Long classId, Long assignmentId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !assignment.getClassId().equals(classInfo.getId())) {
            throw new NotFoundException("作业不存在");
        }

        boolean canWrite = permissionService.hasPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);
        if (!canWrite && !"PUBLISHED".equals(assignment.getStatus()) && !"CLOSED".equals(assignment.getStatus())) {
            throw new NotFoundException("作业不存在");
        }

        Long memberCount = classStudentRelMapper.selectCount(
                new LambdaQueryWrapper<ClassStudentRel>()
                        .eq(ClassStudentRel::getClassId, classInfo.getId()));

        List<AssignmentOverviewVO.ProblemStatItem> problems =
                classInfoMapper.selectAssignmentProblemStats(classInfo.getId(), assignment.getProblemSetId());
        List<AssignmentOverviewVO.StudentStatItem> students =
                classInfoMapper.selectAssignmentStudentStats(classInfo.getId(), assignment.getProblemSetId(), assignment.getDeadline());

        int problemCount = problems != null ? problems.size() : 0;
        double avgRate = 0;
        if (students != null && !students.isEmpty() && problemCount > 0) {
            double totalRate = students.stream()
                    .mapToDouble(s -> s.getAcCount() * 100.0 / problemCount)
                    .sum();
            avgRate = Math.round(totalRate / students.size() * 10.0) / 10.0;
        }

        AssignmentOverviewVO vo = new AssignmentOverviewVO();
        vo.setAssignmentId(assignment.getId());
        vo.setTitle(assignment.getTitle());
        vo.setDeadline(assignment.getDeadline());
        vo.setMemberCount(memberCount != null ? memberCount.intValue() : 0);
        vo.setProblemCount(problemCount);
        vo.setAvgCompletionRate(avgRate);
        vo.setProblems(problems != null ? problems : Collections.emptyList());
        vo.setStudents(students != null ? students : Collections.emptyList());
        return vo;
    }

    private void validatePageParam(Integer current, Integer size) {
        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
        }
    }

    private ClassItemVO toClassItemVO(ClassInfo classInfo) {
        ClassItemVO vo = new ClassItemVO();
        vo.setClassId(classInfo.getId());
        vo.setName(classInfo.getName());
        vo.setDescription(classInfo.getDescription() == null ? "" : classInfo.getDescription());
        vo.setIsPublic(Boolean.TRUE.equals(classInfo.getIsPublic()));
        vo.setCreatorId(classInfo.getCreatedByUserId());
        return vo;
    }

    private LinkPageVO toLinkPageVO(IPage<LinkPageItemVO> pageResult) {
        LinkPageVO vo = new LinkPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        List<LinkPageItemVO> records = pageResult.getRecords();
        vo.setRecords(records == null ? Collections.emptyList() : records);
        return vo;
    }

    private String normalizeRequiredText(String raw, String message) {
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

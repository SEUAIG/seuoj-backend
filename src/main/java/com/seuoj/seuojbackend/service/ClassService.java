package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.classinfo.ClassBatchImportDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassCreateDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassUpdateDTO;
import com.seuoj.seuojbackend.entity.ClassContestRel;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.ClassStudentRel;
import com.seuoj.seuojbackend.entity.ClassProblemSetRel;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ClassContestRelMapper;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ClassStudentRelMapper;
import com.seuoj.seuojbackend.mapper.ClassProblemSetRelMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.vo.classinfo.ClassBatchImportResultVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassCreateVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassProblemSetMatrixVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageItemVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageVO;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ClassService {

    private final ClassInfoMapper classInfoMapper;
    private final ClassStudentRelMapper classStudentRelMapper;
    private final ClassProblemSetRelMapper classProblemSetRelMapper;
    private final ClassContestRelMapper classContestRelMapper;
    private final ProblemSetMapper problemSetMapper;
    private final ContestMapper contestMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserRoleService userRoleService;
    private final UserRoleMapper userRoleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public ClassService(ClassInfoMapper classInfoMapper,
            ClassStudentRelMapper classStudentRelMapper,
            ClassProblemSetRelMapper classProblemSetRelMapper,
            ClassContestRelMapper classContestRelMapper,
            ProblemSetMapper problemSetMapper,
            ContestMapper contestMapper,
            UserInfoMapper userInfoMapper,
            UserRoleService userRoleService,
            UserRoleMapper userRoleMapper,
            UserRoleRelMapper userRoleRelMapper,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender) {
        this.classInfoMapper = classInfoMapper;
        this.classStudentRelMapper = classStudentRelMapper;
        this.classProblemSetRelMapper = classProblemSetRelMapper;
        this.classContestRelMapper = classContestRelMapper;
        this.problemSetMapper = problemSetMapper;
        this.contestMapper = contestMapper;
        this.userInfoMapper = userInfoMapper;
        this.userRoleService = userRoleService;
        this.userRoleMapper = userRoleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    /**
     * 创建班级
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassCreateVO createClass(ClassCreateDTO dto) {
        Long userId = AuthContexts.userIdOrNull();
        assertTeacherOrAdmin(userId);

        boolean isTeacher = userRoleService.isTeacher(userId);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setPublicId(UUID.randomUUID().toString());
        classInfo.setName(normalizeRequiredText(dto.getName(), "name 不能为空"));
        classInfo.setDescription(dto.getDescription());
        classInfo.setIsPublic(Boolean.TRUE.equals(dto.getIsPublic()));
        classInfo.setTeacherUserId(isTeacher ? userId : null);
        classInfoMapper.insert(classInfo);

        ClassCreateVO vo = new ClassCreateVO();
        vo.setClassPublicId(classInfo.getPublicId());
        return vo;
    }

    /**
     * 分页查询班级列表
     */
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

    /**
     * 更新班级基础信息
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassItemVO updateClass(String classPublicId, ClassUpdateDTO dto) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

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

    /**
     * 删除班级及其关联关系
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteClass(String classPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

        classInfoMapper.deleteById(classInfo.getId());

        classStudentRelMapper.update(null, new LambdaUpdateWrapper<ClassStudentRel>()
                .set(ClassStudentRel::getIsDel, 1)
                .eq(ClassStudentRel::getClassId, classInfo.getId()));

        classProblemSetRelMapper.update(null, new LambdaUpdateWrapper<ClassProblemSetRel>()
                .set(ClassProblemSetRel::getIsDel, 1)
                .eq(ClassProblemSetRel::getClassId, classInfo.getId()));

        classContestRelMapper.update(null, new LambdaUpdateWrapper<ClassContestRel>()
                .set(ClassContestRel::getIsDel, 1)
                .eq(ClassContestRel::getClassId, classInfo.getId()));
    }

    /**
     * 当前用户以学生身份加入班级
     */
    @Transactional(rollbackFor = Exception.class)
    public void joinClass(String classPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();

        // TODO: 是否有必要对于管理员/教师做加入限制？但是可能有学生管理员或者学生助教

        if (classInfo.getTeacherUserId() != null && classInfo.getTeacherUserId().equals(userId)) {
            throw new ForbiddenException("班级创建者不能作为班级学生加入");
        }

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

    /**
     * 分页查询班级成员
     */
    public ClassMemberPageVO getClassMemberPage(String classPublicId, Integer current, Integer size) {
        validatePageParam(current, size);

        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanViewClassRelated(classInfo, userId);

        IPage<ClassMemberItemVO> pageResult = classInfoMapper.selectClassMemberPage(new Page<>(current, size),
                classInfo.getId());
        ClassMemberPageVO vo = new ClassMemberPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(pageResult.getRecords() == null ? Collections.emptyList() : pageResult.getRecords());
        return vo;
    }

    /**
     * 移除班级成员
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(String classPublicId, String userPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long currentUserId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, currentUserId);

        Long userId = findUserIdByPublicId(userPublicId);
        if (classInfo.getTeacherUserId() != null && userId.equals(classInfo.getTeacherUserId())) {
            throw new BadRequestException("不能移除班级教师");
        }

        int updated = classStudentRelMapper.update(null, new LambdaUpdateWrapper<ClassStudentRel>()
                .set(ClassStudentRel::getIsDel, 1)
                .eq(ClassStudentRel::getClassId, classInfo.getId())
                .eq(ClassStudentRel::getUserId, userId));
        if (updated == 0) {
            throw new NotFoundException("班级成员不存在");
        }
    }

    /**
     * 添加班级成员
     */
    @Transactional(rollbackFor = Exception.class)
    public void addMember(String classPublicId, String userPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long currentUserId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, currentUserId);

        Long userId = findUserIdByPublicId(userPublicId);
        if (classInfo.getTeacherUserId() != null && userId.equals(classInfo.getTeacherUserId())) {
            throw new BadRequestException("班级教师无需添加");
        }

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

    /**
     * 分页查询班级关联题单
     */
    public LinkPageVO getClassProblemSetPage(String classPublicId, Integer current, Integer size) {
        validatePageParam(current, size);

        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanViewClassRelated(classInfo, userId);

        IPage<LinkPageItemVO> pageResult = classInfoMapper.selectClassProblemSetPage(new Page<>(current, size),
                classInfo.getId());
        return toLinkPageVO(pageResult);
    }

    /**
     * 关联题单到班级
     */
    @Transactional(rollbackFor = Exception.class)
    public void linkProblemSet(String classPublicId, String problemSetPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

        ProblemSet problemSet = getProblemSetByPublicId(problemSetPublicId);

        int restored = classProblemSetRelMapper.restoreDeletedProblemSetLink(classInfo.getId(), problemSet.getId());
        if (restored > 0) {
            return;
        }

        ClassProblemSetRel rel = new ClassProblemSetRel();
        rel.setClassId(classInfo.getId());
        rel.setProblemSetId(problemSet.getId());
        try {
            classProblemSetRelMapper.insert(rel);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("班级与题单已关联");
        }
    }

    /**
     * 取消班级与题单关联
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlinkProblemSet(String classPublicId, String problemSetPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

        ProblemSet problemSet = getProblemSetByPublicId(problemSetPublicId);

        int updated = classProblemSetRelMapper.update(null, new LambdaUpdateWrapper<ClassProblemSetRel>()
                .set(ClassProblemSetRel::getIsDel, 1)
                .eq(ClassProblemSetRel::getClassId, classInfo.getId())
                .eq(ClassProblemSetRel::getProblemSetId, problemSet.getId()));
        if (updated == 0) {
            throw new NotFoundException("班级与题单关联不存在");
        }
    }

    /**
     * 分页查询班级关联比赛
     */
    public LinkPageVO getClassContestPage(String classPublicId, Integer current, Integer size) {
        validatePageParam(current, size);

        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanViewClassRelated(classInfo, userId);

        IPage<LinkPageItemVO> pageResult = classInfoMapper.selectClassContestPage(new Page<>(current, size),
                classInfo.getId());
        return toLinkPageVO(pageResult);
    }

    /**
     * 关联比赛到班级
     */
    @Transactional(rollbackFor = Exception.class)
    public void linkContest(String classPublicId, String contestPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

        Contest contest = getContestByPublicId(contestPublicId);

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

    /**
     * 取消班级与比赛关联
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlinkContest(String classPublicId, String contestPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

        Contest contest = getContestByPublicId(contestPublicId);

        int updated = classContestRelMapper.update(null, new LambdaUpdateWrapper<ClassContestRel>()
                .set(ClassContestRel::getIsDel, 1)
                .eq(ClassContestRel::getClassId, classInfo.getId())
                .eq(ClassContestRel::getContestId, contest.getId()));
        if (updated == 0) {
            throw new NotFoundException("班级与比赛关联不存在");
        }
    }

    /**
     * 批量导入学生到班级
     */
    public ClassBatchImportResultVO batchImportStudents(String classPublicId, ClassBatchImportDTO dto) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long currentUserId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, currentUserId);

        List<ClassBatchImportDTO.StudentRow> students = dto.getStudents();
        boolean isRandomMode = "random".equals(dto.getPasswordMode());
        boolean shouldSendEmail = dto.isSendEmail();

        ClassBatchImportResultVO result = new ClassBatchImportResultVO();
        result.setTotalCount(students.size());

        // 查找默认 USER 角色
        UserRole defaultRole = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleCode, RoleType.USER.getCode())
                .eq(UserRole::getIsDel, 0));
        if (defaultRole == null) {
            throw new BadRequestException("默认角色 USER 不存在");
        }

        // 前置校验密码模式一致性
        for (int i = 0; i < students.size(); i++) {
            ClassBatchImportDTO.StudentRow row = students.get(i);
            String pwd = row.getPassword();
            boolean hasPwd = pwd != null && !pwd.trim().isEmpty();
            if (!isRandomMode && !hasPwd) {
                result.getFailures().add(new ClassBatchImportResultVO.FailDetail(
                        i + 1, row.getStudentId(), row.getName(), "指定密码模式下密码不能为空"));
            }
            if (isRandomMode && hasPwd) {
                result.getFailures().add(new ClassBatchImportResultVO.FailDetail(
                        i + 1, row.getStudentId(), row.getName(), "随机密码模式下不应提供密码"));
            }
        }
        if (!result.getFailures().isEmpty()) {
            result.setSuccessCount(0);
            result.setFailCount(result.getFailures().size());
            return result;
        }

        int successCount = 0;
        for (int i = 0; i < students.size(); i++) {
            ClassBatchImportDTO.StudentRow row = students.get(i);
            try {
                String studentId = row.getStudentId().trim();
                String name = row.getName().trim();
                String email = (studentId + "@seu.edu.cn").toLowerCase();

                // 检查用户是否已存在（按邮箱查找）
                UserInfo existingUser = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getEmail, email));

                String rawPassword;
                boolean isExisting = false;

                if (existingUser != null) {
                    // 已有账号，检查是否已在班级中
                    if (isStudentInClass(classInfo.getId(), existingUser.getId())) {
                        result.getFailures().add(new ClassBatchImportResultVO.FailDetail(
                                i + 1, studentId, name, "该学生已在班级中"));
                        continue;
                    }
                    isExisting = true;
                    rawPassword = "(已有账号)";

                    // 将已有用户加入班级
                    addStudentToClass(classInfo, existingUser.getId());
                } else {
                    // 创建新账号
                    rawPassword = isRandomMode ? generateRandomPassword() : row.getPassword().trim();

                    UserInfo newUser = new UserInfo();
                    newUser.setUsername(name);
                    newUser.setEmail(email);
                    newUser.setPublicId(UUID.randomUUID().toString());
                    newUser.setPassword(passwordEncoder.encode(rawPassword));

                    try {
                        userInfoMapper.insert(newUser);
                    } catch (DuplicateKeyException e) {
                        result.getFailures().add(new ClassBatchImportResultVO.FailDetail(
                                i + 1, studentId, name, "邮箱或用户名已存在(并发冲突)"));
                        continue;
                    }

                    // 分配 USER 角色
                    UserRoleRel rel = new UserRoleRel();
                    rel.setUserId(newUser.getId());
                    rel.setRoleId(defaultRole.getId());
                    userRoleRelMapper.insert(rel);

                    // 将新用户加入班级
                    addStudentToClass(classInfo, newUser.getId());

                    // 发送通知邮件
                    if (shouldSendEmail) {
                        try {
                            sendClassAccountNotificationEmail(email, name, rawPassword, classInfo.getName());
                        } catch (Exception e) {
                            log.warn("班级账号通知邮件发送失败: {}, {}", email, e.getMessage());
                        }
                    }
                }

                result.getSuccesses().add(new ClassBatchImportResultVO.SuccessDetail(
                        i + 1, studentId, name, email, rawPassword, isExisting));
                successCount++;
            } catch (Exception e) {
                result.getFailures().add(new ClassBatchImportResultVO.FailDetail(
                        i + 1, row.getStudentId(), row.getName(), "系统异常: " + e.getMessage()));
            }
        }

        result.setSuccessCount(successCount);
        result.setFailCount(result.getTotalCount() - successCount);
        return result;
    }

    /**
     * 班级学情概览
     */
    public ClassOverviewVO getClassOverview(String classPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

        Long memberCount = classStudentRelMapper.selectCount(new LambdaQueryWrapper<ClassStudentRel>()
                .eq(ClassStudentRel::getClassId, classInfo.getId())
                .eq(ClassStudentRel::getIsDel, 0));

        List<Map<String, Object>> stats = classInfoMapper.selectClassOverviewStats(classInfo.getId());

        int totalProblems = 0;
        List<ClassOverviewVO.ProblemSetProgressItem> psItems = new ArrayList<>();

        for (Map<String, Object> row : stats) {
            ClassOverviewVO.ProblemSetProgressItem item = new ClassOverviewVO.ProblemSetProgressItem();
            item.setProblemSetPublicId((String) row.get("problem_set_public_id"));
            item.setTitle((String) row.get("title"));
            int problemCount = ((Number) row.get("problem_count")).intValue();
            int studentAcCount = ((Number) row.get("total_student_ac_count")).intValue();
            item.setProblemCount(problemCount);

            long mc = memberCount != null ? memberCount : 0;
            if (mc > 0 && problemCount > 0) {
                item.setAvgCompletionRate(
                        Math.round(studentAcCount * 10000.0 / (mc * problemCount)) / 100.0);
            } else {
                item.setAvgCompletionRate(0);
            }
            psItems.add(item);

            totalProblems += problemCount;
        }

        // 每位学生的 AC 统计
        List<Map<String, Object>> studentStats = classInfoMapper.selectClassStudentAcStats(classInfo.getId());
        List<ClassOverviewVO.StudentOverviewItem> studentItems = new ArrayList<>();
        for (Map<String, Object> row : studentStats) {
            ClassOverviewVO.StudentOverviewItem si = new ClassOverviewVO.StudentOverviewItem();
            si.setUserPublicId((String) row.get("user_public_id"));
            si.setUsername((String) row.get("username"));
            si.setAcCount(((Number) row.get("ac_count")).intValue());
            studentItems.add(si);
        }

        ClassOverviewVO vo = new ClassOverviewVO();
        vo.setMemberCount(memberCount != null ? memberCount.intValue() : 0);
        vo.setTotalProblems(totalProblems);
        vo.setProblemSets(psItems);
        vo.setStudents(studentItems);
        return vo;
    }

    /**
     * 班级题单做题矩阵
     */
    public ClassProblemSetMatrixVO getClassProblemSetMatrix(String classPublicId, String problemSetPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.userIdOrNull();
        assertCanManageClass(classInfo, userId);

        ProblemSet problemSet = getProblemSetByPublicId(problemSetPublicId);

        // 校验题单是否关联到该班级
        Long relCount = classProblemSetRelMapper.selectCount(new LambdaQueryWrapper<ClassProblemSetRel>()
                .eq(ClassProblemSetRel::getClassId, classInfo.getId())
                .eq(ClassProblemSetRel::getProblemSetId, problemSet.getId())
                .eq(ClassProblemSetRel::getIsDel, 0));
        if (relCount == null || relCount == 0) {
            throw new NotFoundException("题单未关联到该班级");
        }

        List<Map<String, Object>> rawData = classInfoMapper.selectClassProblemSetMatrixRaw(
                classInfo.getId(), problemSet.getId());

        // 收集题目列 (保持 sortOrder 顺序)
        LinkedHashMap<String, ClassProblemSetMatrixVO.ProblemColumn> problemMap = new LinkedHashMap<>();
        // 收集学生行
        LinkedHashMap<String, ClassProblemSetMatrixVO.StudentRow> studentMap = new LinkedHashMap<>();
        // 学生 -> pid -> status
        LinkedHashMap<String, LinkedHashMap<String, Integer>> statusMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rawData) {
            String pid = (String) row.get("pid");
            String problemTitle = (String) row.get("problem_title");
            int sortOrder = ((Number) row.get("sort_order")).intValue();
            String userPublicId = (String) row.get("user_public_id");
            String username = (String) row.get("username");
            int bestStatus = ((Number) row.get("best_status")).intValue();

            problemMap.computeIfAbsent(pid, k -> {
                ClassProblemSetMatrixVO.ProblemColumn col = new ClassProblemSetMatrixVO.ProblemColumn();
                col.setPid(k);
                col.setTitle(problemTitle);
                col.setSortOrder(sortOrder);
                return col;
            });

            studentMap.computeIfAbsent(userPublicId, k -> {
                ClassProblemSetMatrixVO.StudentRow sr = new ClassProblemSetMatrixVO.StudentRow();
                sr.setUserPublicId(k);
                sr.setUsername(username);
                return sr;
            });

            statusMap.computeIfAbsent(userPublicId, k -> new LinkedHashMap<>())
                    .put(pid, bestStatus);
        }

        List<ClassProblemSetMatrixVO.ProblemColumn> problems = new ArrayList<>(problemMap.values());
        List<ClassProblemSetMatrixVO.StudentRow> students = new ArrayList<>(studentMap.values());

        // 构建每个学生的 cells
        for (ClassProblemSetMatrixVO.StudentRow student : students) {
            LinkedHashMap<String, Integer> userStatus = statusMap.getOrDefault(student.getUserPublicId(),
                    new LinkedHashMap<>());
            List<String> cells = new ArrayList<>();
            int acCount = 0;
            for (ClassProblemSetMatrixVO.ProblemColumn prob : problems) {
                int st = userStatus.getOrDefault(prob.getPid(), 0);
                if (st == 2) {
                    cells.add("AC");
                    acCount++;
                } else if (st == 1) {
                    cells.add("ATTEMPTED");
                } else {
                    cells.add("NOT_ATTEMPTED");
                }
            }
            student.setCells(cells);
            student.setAcCount(acCount);
        }

        ClassProblemSetMatrixVO vo = new ClassProblemSetMatrixVO();
        vo.setProblemSetTitle(problemSet.getTitle());
        vo.setProblems(problems);
        vo.setStudents(students);
        return vo;
    }

    private void addStudentToClass(ClassInfo classInfo, Long userId) {
        if (classInfo.getTeacherUserId() != null && userId.equals(classInfo.getTeacherUserId())) {
            throw new BadRequestException("班级教师无需作为学生添加");
        }
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
            // 已在班级中，忽略
        }
    }

    private boolean isStudentInClass(Long classId, Long userId) {
        Long count = classStudentRelMapper.selectCount(new LambdaQueryWrapper<ClassStudentRel>()
                .eq(ClassStudentRel::getClassId, classId)
                .eq(ClassStudentRel::getUserId, userId)
                .eq(ClassStudentRel::getIsDel, 0));
        return count != null && count > 0;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void sendClassAccountNotificationEmail(String to, String username, String rawPassword, String className) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("【SEUOJ】您的账号已创建");
        message.setText("您好，\n\n老师已为您创建 SEUOJ 账号并将您加入班级「" + className + "」，请使用以下信息登录：\n\n"
                + "邮箱：" + to + "\n"
                + "用户名：" + username + "\n"
                + "初始密码：" + rawPassword + "\n\n"
                + "请登录后尽快修改密码。\n\n"
                + "—— SEUOJ 团队");
        mailSender.send(message);
    }

    /**
     * 校验分页参数范围
     */
    private void validatePageParam(Integer current, Integer size) {
        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
        }
    }

    /**
     * 按 public_id 查询班级
     */
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

    /**
     * 按 public_id 查询题单
     */
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

    /**
     * 按 public_id 查询比赛
     */
    private Contest getContestByPublicId(String contestPublicId) {
        if (!StringUtils.hasText(contestPublicId)) {
            throw new BadRequestException("contest_public_id 不能为空");
        }
        Contest contest = contestMapper.selectOne(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getPublicId, contestPublicId));
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }
        return contest;
    }

    /**
     * 按 public_id 查询用户主键
     */
    private Long findUserIdByPublicId(String userPublicId) {
        if (!StringUtils.hasText(userPublicId)) {
            throw new BadRequestException("user_public_id 不能为空");
        }
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getPublicId, userPublicId));
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user.getId();
    }

    /**
     * 班级实体转展示对象
     */
    private ClassItemVO toClassItemVO(ClassInfo classInfo) {
        ClassItemVO vo = new ClassItemVO();
        vo.setClassPublicId(classInfo.getPublicId());
        vo.setName(classInfo.getName());
        vo.setDescription(classInfo.getDescription() == null ? "" : classInfo.getDescription());
        vo.setIsPublic(Boolean.TRUE.equals(classInfo.getIsPublic()));

        String creatorPublicId = "";
        if (classInfo.getTeacherUserId() != null) {
            UserInfo teacher = userInfoMapper.selectById(classInfo.getTeacherUserId());
            if (teacher != null) {
                creatorPublicId = teacher.getPublicId();
            }
        }
        vo.setCreatorPublicId(creatorPublicId);
        return vo;
    }

    /**
     * 链接分页结果转换
     */
    private LinkPageVO toLinkPageVO(IPage<LinkPageItemVO> pageResult) {
        LinkPageVO vo = new LinkPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        List<LinkPageItemVO> records = pageResult.getRecords();
        vo.setRecords(records == null ? Collections.emptyList() : records);
        return vo;
    }

    /**
     * 校验当前用户是否可管理班级
     */
    private void assertCanManageClass(ClassInfo classInfo, Long userId) {
        if (userRoleService.isAdmin(userId)) {
            return;
        }
        if (!userRoleService.isTeacher(userId)) {
            throw new ForbiddenException("无权管理该班级");
        }
        if (classInfo.getTeacherUserId() == null || !userId.equals(classInfo.getTeacherUserId())) {
            throw new ForbiddenException("无权管理该班级");
        }
    }

    /**
     * 校验当前用户是否可访问班级关联资源
     */
    private void assertCanViewClassRelated(ClassInfo classInfo, Long userId) {
        if (userRoleService.isAdmin(userId)) {
            return;
        }
        if (classInfo.getTeacherUserId() != null && userId.equals(classInfo.getTeacherUserId())) {
            return;
        }

        Long count = classStudentRelMapper.selectCount(new LambdaQueryWrapper<ClassStudentRel>()
                .eq(ClassStudentRel::getClassId, classInfo.getId())
                .eq(ClassStudentRel::getUserId, userId));
        if (count != null && count > 0) {
            return;
        }

        throw new ForbiddenException("无权访问该班级资源");
    }

    /**
     * 校验当前用户是否为教师或管理员
     */
    private void assertTeacherOrAdmin(Long userId) {
        if (!userRoleService.isTeacherOrAdmin(userId)) {
            throw new ForbiddenException("仅教师或管理员可创建班级");
        }
    }

    /**
     * 标准化并校验必填文本
     */
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

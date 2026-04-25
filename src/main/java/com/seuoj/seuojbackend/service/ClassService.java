package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.announcement.AttachmentDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassBatchImportDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassCreateDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassUpdateDTO;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.AssignmentProblemRel;
import com.seuoj.seuojbackend.entity.ClassContestRel;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.ClassIntroAttachment;
import com.seuoj.seuojbackend.entity.ClassStudentRel;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.entity.ProblemSetProblemRel;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.AssignmentProblemRelMapper;
import com.seuoj.seuojbackend.mapper.ClassContestRelMapper;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ClassIntroAttachmentMapper;
import com.seuoj.seuojbackend.mapper.ClassStudentRelMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetProblemRelMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.vo.classinfo.AssignmentOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassBatchImportResultVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassCreateVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassIntroAttachmentVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassPageVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageItemVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageVO;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
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
    private final ClassIntroAttachmentMapper classIntroAttachmentMapper;
    private final ClassStudentRelMapper classStudentRelMapper;
    private final ClassContestRelMapper classContestRelMapper;
    private final ContestMapper contestMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemRelMapper assignmentProblemRelMapper;
    private final ProblemSetMapper problemSetMapper;
    private final ProblemSetProblemRelMapper problemSetProblemRelMapper;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;
    private final UserInfoMapper userInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public ClassService(ClassInfoMapper classInfoMapper,
                        ClassIntroAttachmentMapper classIntroAttachmentMapper,
                        ClassStudentRelMapper classStudentRelMapper,
                        ClassContestRelMapper classContestRelMapper,
                        ContestMapper contestMapper,
                        AssignmentMapper assignmentMapper,
                        AssignmentProblemRelMapper assignmentProblemRelMapper,
                        ProblemSetMapper problemSetMapper,
                        ProblemSetProblemRelMapper problemSetProblemRelMapper,
                        PermissionService permissionService,
                        UserRoleService userRoleService,
                        UserInfoMapper userInfoMapper,
                        UserRoleMapper userRoleMapper,
                        UserRoleRelMapper userRoleRelMapper,
                        PasswordEncoder passwordEncoder,
                        JavaMailSender mailSender) {
        this.classInfoMapper = classInfoMapper;
        this.classIntroAttachmentMapper = classIntroAttachmentMapper;
        this.classStudentRelMapper = classStudentRelMapper;
        this.classContestRelMapper = classContestRelMapper;
        this.contestMapper = contestMapper;
        this.assignmentMapper = assignmentMapper;
        this.assignmentProblemRelMapper = assignmentProblemRelMapper;
        this.problemSetMapper = problemSetMapper;
        this.problemSetProblemRelMapper = problemSetProblemRelMapper;
        this.permissionService = permissionService;
        this.userRoleService = userRoleService;
        this.userInfoMapper = userInfoMapper;
        this.userRoleMapper = userRoleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
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
        for (ClassItemVO record : vo.getRecords()) {
            record.setCanWrite(isAdmin || (userId != null &&
                    permissionService.hasPermission(userId, ResourceType.CLASS, record.getClassId(), PermissionOp.WRITE)));
        }
        return vo;
    }

    public ClassItemVO getClassDetail(Long classId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);
        ClassItemVO vo = toClassItemVO(classInfo);
        vo.setCanWrite(permissionService.hasPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE));
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
        if (dto.getIntroduction() != null) {
            update.setIntroduction(dto.getIntroduction());
            changed = true;
        }
        if (dto.getIsPublic() != null) {
            update.setIsPublic(dto.getIsPublic());
            changed = true;
        }

        if (changed) {
            classInfoMapper.updateById(update);
        }

        // Handle intro attachments
        if (dto.getRemoveIntroAttachmentIds() != null && !dto.getRemoveIntroAttachmentIds().isEmpty()) {
            for (Long attachId : dto.getRemoveIntroAttachmentIds()) {
                ClassIntroAttachment att = classIntroAttachmentMapper.selectById(attachId);
                if (att != null && att.getClassId().equals(classInfo.getId())) {
                    classIntroAttachmentMapper.deleteById(attachId);
                }
            }
        }
        if (dto.getAddIntroAttachments() != null && !dto.getAddIntroAttachments().isEmpty()) {
            for (AttachmentDTO attDto : dto.getAddIntroAttachments()) {
                ClassIntroAttachment att = new ClassIntroAttachment();
                att.setClassId(classInfo.getId());
                att.setFilePath(attDto.getFilePath());
                att.setFileName(attDto.getFileName());
                att.setFileSize(attDto.getFileSize());
                classIntroAttachmentMapper.insert(att);
            }
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

        assignmentMapper.update(null, new LambdaUpdateWrapper<Assignment>()
                .set(Assignment::getIsDel, 1)
                .eq(Assignment::getClassId, classInfo.getId()));
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

    @Transactional(rollbackFor = Exception.class)
    public ClassBatchImportResultVO batchImportMembers(Long classId, ClassBatchImportDTO dto) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long currentUserId = AuthContexts.requiredUserId();
        permissionService.assertPermission(currentUserId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        boolean isRandomMode = "random".equals(dto.getPasswordMode());
        boolean shouldSendEmail = dto.isSendEmail();

        UserRole defaultRole = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleCode, RoleType.STUDENT.getCode())
                .eq(UserRole::getIsDel, 0));
        if (defaultRole == null) {
            throw new BadRequestException("默认角色 STUDENT 不存在");
        }

        ClassBatchImportResultVO result = new ClassBatchImportResultVO();
        result.setTotalCount(dto.getStudents().size());

        int successCount = 0;
        int skippedCount = 0;
        int failCount = 0;

        for (int i = 0; i < dto.getStudents().size(); i++) {
            ClassBatchImportDTO.StudentRow row = dto.getStudents().get(i);
            try {
                String username = row.getStudentId().trim();
                if (username.isEmpty()) {
                    result.getRows().add(new ClassBatchImportResultVO.RowResult(
                            i + 1, row.getStudentId(), row.getName(), "忽略: 学号为空"));
                    failCount++;
                    continue;
                }

                String email = username.toLowerCase() + "@seu.edu.cn";
                String name = (row.getName() != null) ? row.getName().trim() : "";

                // 查找用户
                UserInfo existingUser = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUsername, username));

                if (existingUser != null) {
                    // 用户已存在，检查是否已在班级中
                    ClassStudentRel existingRel = classStudentRelMapper.selectOne(
                            new LambdaQueryWrapper<ClassStudentRel>()
                                    .eq(ClassStudentRel::getClassId, classId)
                                    .eq(ClassStudentRel::getUserId, existingUser.getId())
                                    .eq(ClassStudentRel::getIsDel, 0));
                    if (existingRel != null) {
                        ClassBatchImportResultVO.RowResult rowResult = new ClassBatchImportResultVO.RowResult(
                                i + 1, username, name.isEmpty() ? (existingUser.getNickname() != null ? existingUser.getNickname() : "") : name, "已加入班级，跳过");
                        rowResult.setEmail(existingUser.getEmail());
                        result.getRows().add(rowResult);
                        skippedCount++;
                    } else {
                        // 恢复或新增班级关系
                        int restored = classStudentRelMapper.restoreDeletedStudent(classId, existingUser.getId());
                        if (restored == 0) {
                            ClassStudentRel rel = new ClassStudentRel();
                            rel.setClassId(classId);
                            rel.setUserId(existingUser.getId());
                            try {
                                classStudentRelMapper.insert(rel);
                            } catch (DuplicateKeyException e) {
                                // 并发情况，忽略
                            }
                        }
                        ClassBatchImportResultVO.RowResult rowResult = new ClassBatchImportResultVO.RowResult(
                                i + 1, username, name.isEmpty() ? (existingUser.getNickname() != null ? existingUser.getNickname() : "") : name, "加入班级");
                        rowResult.setEmail(existingUser.getEmail());
                        result.getRows().add(rowResult);
                        successCount++;
                    }
                } else {
                    // 用户不存在，创建账号并加入班级
                    String rawPassword = isRandomMode ? generateRandomPassword() : row.getPassword().trim();

                    UserInfo newUser = new UserInfo();
                    newUser.setUsername(username);
                    if (!name.isEmpty()) {
                        newUser.setNickname(name);
                    }
                    newUser.setEmail(email);
                    newUser.setPassword(passwordEncoder.encode(rawPassword));

                    try {
                        userInfoMapper.insert(newUser);
                    } catch (DuplicateKeyException e) {
                        ClassBatchImportResultVO.RowResult rowResult = new ClassBatchImportResultVO.RowResult(
                                i + 1, username, name, "忽略: 用户名或邮箱已存在");
                        result.getRows().add(rowResult);
                        failCount++;
                        continue;
                    }

                    UserRoleRel roleRel = new UserRoleRel();
                    roleRel.setUserId(newUser.getId());
                    roleRel.setRoleId(defaultRole.getId());
                    userRoleRelMapper.insert(roleRel);

                    ClassStudentRel rel = new ClassStudentRel();
                    rel.setClassId(classId);
                    rel.setUserId(newUser.getId());
                    try {
                        classStudentRelMapper.insert(rel);
                    } catch (DuplicateKeyException e) {
                        // 并发情况，忽略
                    }

                    if (shouldSendEmail) {
                        try {
                            sendAccountNotificationEmail(email, username, rawPassword);
                        } catch (Exception e) {
                            log.warn("账号通知邮件发送失败: {}, {}", email, e.getMessage());
                        }
                    }

                    ClassBatchImportResultVO.RowResult rowResult = new ClassBatchImportResultVO.RowResult(
                            i + 1, username, name, "创建账号并加入班级");
                    rowResult.setEmail(email);
                    rowResult.setPassword(rawPassword);
                    result.getRows().add(rowResult);
                    successCount++;
                }
            } catch (Exception e) {
                ClassBatchImportResultVO.RowResult rowResult = new ClassBatchImportResultVO.RowResult(
                        i + 1, row.getStudentId(), row.getName(), "忽略: " + e.getMessage());
                result.getRows().add(rowResult);
                failCount++;
            }
        }

        result.setSuccessCount(successCount);
        result.setSkippedCount(skippedCount);
        result.setFailCount(failCount);
        return result;
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

    private void sendAccountNotificationEmail(String to, String username, String rawPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("【SEUOJ】您的账号已创建");
        message.setText("您好，\n\n管理员已为您创建 SEUOJ 账号，请使用以下信息登录：\n\n"
                + "邮箱：" + to + "\n"
                + "用户名：" + username + "\n"
                + "密码：" + rawPassword + "\n\n"
                + "登录后请及时修改密码。\n\n"
                + "SEUOJ 团队");
        mailSender.send(message);
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

    public LinkPageVO getClassProblemSetPage(Long classId, Integer current, Integer size) {
        throw new BadRequestException("作业已与题单解绑，请使用作业列表接口");
    }

    @Transactional(rollbackFor = Exception.class)
    public void linkProblemSet(Long classId, Long problemSetId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        ProblemSet problemSet = problemSetMapper.selectById(problemSetId);
        if (problemSet == null) {
            throw new NotFoundException("题单不存在");
        }

        Assignment assignment = new Assignment();
        assignment.setClassId(classInfo.getId());
        assignment.setTitle(problemSet.getTitle());
        assignment.setStatus("PUBLISHED");
        assignment.setCreatedByUserId(userId);
        assignmentMapper.insert(assignment);

        List<ProblemSetProblemRel> psRels = problemSetProblemRelMapper.selectList(
                new LambdaQueryWrapper<ProblemSetProblemRel>()
                        .eq(ProblemSetProblemRel::getProblemSetId, problemSet.getId())
                        .orderByAsc(ProblemSetProblemRel::getSortOrder));
        int sortOrder = 1;
        for (ProblemSetProblemRel psRel : psRels) {
            AssignmentProblemRel rel = new AssignmentProblemRel();
            rel.setAssignmentId(assignment.getId());
            rel.setProblemId(psRel.getProblemId());
            rel.setSortOrder(sortOrder++);
            rel.setWeight(1);
            assignmentProblemRelMapper.insert(rel);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlinkProblemSet(Long classId, Long problemSetId) {
        throw new BadRequestException("作业已与题单解绑，请使用删除作业接口");
    }

    public ClassOverviewVO getClassOverview(Long classId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

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

    public List<ClassOverviewVO.AssignmentProgressItem> getAssignmentProgress(Long classId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);
        List<ClassOverviewVO.AssignmentProgressItem> assignments = classInfoMapper.selectAssignmentProgress(classInfo.getId());
        return assignments != null ? assignments : Collections.emptyList();
    }

    public AssignmentOverviewVO getAssignmentOverview(Long classId, Long assignmentId) {
        Long userId = AuthContexts.requiredUserId();
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !assignment.getClassId().equals(classInfo.getId())) {
            throw new NotFoundException("作业不存在");
        }

        Long memberCount = classStudentRelMapper.selectCount(
                new LambdaQueryWrapper<ClassStudentRel>()
                        .eq(ClassStudentRel::getClassId, classInfo.getId()));

        List<AssignmentOverviewVO.ProblemStatItem> problems =
                classInfoMapper.selectAssignmentProblemStats(classInfo.getId(), assignment.getId());
        List<AssignmentOverviewVO.StudentStatItem> students =
                classInfoMapper.selectAssignmentStudentStats(classInfo.getId(), assignment.getId(), assignment.getDeadline());

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
        vo.setIntroduction(classInfo.getIntroduction());
        vo.setIsPublic(Boolean.TRUE.equals(classInfo.getIsPublic()));
        vo.setCreatorId(classInfo.getCreatedByUserId());

        // Fill intro attachments
        List<ClassIntroAttachment> attachments = classIntroAttachmentMapper.selectList(
                new LambdaQueryWrapper<ClassIntroAttachment>()
                        .eq(ClassIntroAttachment::getClassId, classInfo.getId())
                        .eq(ClassIntroAttachment::getIsDel, 0)
                        .orderByAsc(ClassIntroAttachment::getCreatedAt));
        if (attachments != null && !attachments.isEmpty()) {
            vo.setIntroAttachments(attachments.stream().map(att -> {
                ClassIntroAttachmentVO attVo = new ClassIntroAttachmentVO();
                attVo.setId(att.getId());
                attVo.setFilePath(att.getFilePath());
                attVo.setFileName(att.getFileName());
                attVo.setFileSize(att.getFileSize());
                return attVo;
            }).toList());
        } else {
            vo.setIntroAttachments(Collections.emptyList());
        }

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

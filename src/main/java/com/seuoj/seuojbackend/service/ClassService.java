package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.dto.classinfo.ClassCreateDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassUpdateDTO;
import com.seuoj.seuojbackend.entity.ClassContestRel;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.ClassStudentRel;
import com.seuoj.seuojbackend.entity.ClassProblemSetRel;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.entity.UserInfo;
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
import com.seuoj.seuojbackend.vo.classinfo.ClassCreateVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassPageVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageItemVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageVO;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    public ClassService(ClassInfoMapper classInfoMapper,
                        ClassStudentRelMapper classStudentRelMapper,
                        ClassProblemSetRelMapper classProblemSetRelMapper,
                        ClassContestRelMapper classContestRelMapper,
                        ProblemSetMapper problemSetMapper,
                        ContestMapper contestMapper,
                        UserInfoMapper userInfoMapper,
                        UserRoleService userRoleService) {
        this.classInfoMapper = classInfoMapper;
        this.classStudentRelMapper = classStudentRelMapper;
        this.classProblemSetRelMapper = classProblemSetRelMapper;
        this.classContestRelMapper = classContestRelMapper;
        this.problemSetMapper = problemSetMapper;
        this.contestMapper = contestMapper;
        this.userInfoMapper = userInfoMapper;
        this.userRoleService = userRoleService;
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

        IPage<ClassMemberItemVO> pageResult = classInfoMapper.selectClassMemberPage(new Page<>(current, size), classInfo.getId());
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

        IPage<LinkPageItemVO> pageResult = classInfoMapper.selectClassProblemSetPage(new Page<>(current, size), classInfo.getId());
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

        IPage<LinkPageItemVO> pageResult = classInfoMapper.selectClassContestPage(new Page<>(current, size), classInfo.getId());
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

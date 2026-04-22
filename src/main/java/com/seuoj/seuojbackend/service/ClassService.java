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
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ClassContestRelMapper;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.mapper.ClassStudentRelMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
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
    private final ClassContestRelMapper classContestRelMapper;
    private final ContestMapper contestMapper;
    private final UserInfoMapper userInfoMapper;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;

    public ClassService(ClassInfoMapper classInfoMapper,
                        ClassStudentRelMapper classStudentRelMapper,
                        ClassContestRelMapper classContestRelMapper,
                        ContestMapper contestMapper,
                        UserInfoMapper userInfoMapper,
                        PermissionService permissionService,
                        UserRoleService userRoleService) {
        this.classInfoMapper = classInfoMapper;
        this.classStudentRelMapper = classStudentRelMapper;
        this.classContestRelMapper = classContestRelMapper;
        this.contestMapper = contestMapper;
        this.userInfoMapper = userInfoMapper;
        this.permissionService = permissionService;
        this.userRoleService = userRoleService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassCreateVO createClass(ClassCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertCanCreate(userId, ResourceType.CLASS);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setPublicId(UUID.randomUUID().toString());
        classInfo.setName(normalizeRequiredText(dto.getName(), "name 不能为空"));
        classInfo.setDescription(dto.getDescription());
        classInfo.setIsPublic(Boolean.TRUE.equals(dto.getIsPublic()));
        classInfo.setCreatedByUserId(userId);
        classInfoMapper.insert(classInfo);

        permissionService.autoGrantCreator(ResourceType.CLASS, classInfo.getId(), userId);

        ClassCreateVO vo = new ClassCreateVO();
        vo.setClassPublicId(classInfo.getPublicId());
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
    public ClassItemVO updateClass(String classPublicId, ClassUpdateDTO dto) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
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
    public void deleteClass(String classPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
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
    public void joinClass(String classPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
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

    public ClassMemberPageVO getClassMemberPage(String classPublicId, Integer current, Integer size) {
        validatePageParam(current, size);

        ClassInfo classInfo = getClassByPublicId(classPublicId);
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
    public void removeMember(String classPublicId, String userPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long currentUserId = AuthContexts.requiredUserId();
        permissionService.assertPermission(currentUserId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Long userId = findUserIdByPublicId(userPublicId);

        int updated = classStudentRelMapper.update(null, new LambdaUpdateWrapper<ClassStudentRel>()
                .set(ClassStudentRel::getIsDel, 1)
                .eq(ClassStudentRel::getClassId, classInfo.getId())
                .eq(ClassStudentRel::getUserId, userId));
        if (updated == 0) {
            throw new NotFoundException("班级成员不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addMember(String classPublicId, String userPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long currentUserId = AuthContexts.requiredUserId();
        permissionService.assertPermission(currentUserId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Long userId = findUserIdByPublicId(userPublicId);

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

    public LinkPageVO getClassContestPage(String classPublicId, Integer current, Integer size) {
        validatePageParam(current, size);

        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        IPage<LinkPageItemVO> pageResult = classInfoMapper.selectClassContestPage(new Page<>(current, size), classInfo.getId());
        return toLinkPageVO(pageResult);
    }

    @Transactional(rollbackFor = Exception.class)
    public void linkContest(String classPublicId, String contestPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

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

    @Transactional(rollbackFor = Exception.class)
    public void unlinkContest(String classPublicId, String contestPublicId) {
        ClassInfo classInfo = getClassByPublicId(classPublicId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        Contest contest = getContestByPublicId(contestPublicId);

        int updated = classContestRelMapper.update(null, new LambdaUpdateWrapper<ClassContestRel>()
                .set(ClassContestRel::getIsDel, 1)
                .eq(ClassContestRel::getClassId, classInfo.getId())
                .eq(ClassContestRel::getContestId, contest.getId()));
        if (updated == 0) {
            throw new NotFoundException("班级与比赛关联不存在");
        }
    }

    private void validatePageParam(Integer current, Integer size) {
        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
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

    private ClassItemVO toClassItemVO(ClassInfo classInfo) {
        ClassItemVO vo = new ClassItemVO();
        vo.setClassPublicId(classInfo.getPublicId());
        vo.setName(classInfo.getName());
        vo.setDescription(classInfo.getDescription() == null ? "" : classInfo.getDescription());
        vo.setIsPublic(Boolean.TRUE.equals(classInfo.getIsPublic()));

        String creatorPublicId = "";
        if (classInfo.getCreatedByUserId() != null) {
            UserInfo creator = userInfoMapper.selectById(classInfo.getCreatedByUserId());
            if (creator != null) {
                creatorPublicId = creator.getPublicId();
            }
        }
        vo.setCreatorPublicId(creatorPublicId);
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

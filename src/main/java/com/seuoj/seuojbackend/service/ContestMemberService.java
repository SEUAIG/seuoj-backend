package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestMembersDTO;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.*;
import com.seuoj.seuojbackend.vo.contest.ContestMembersVO;
import java.time.LocalDateTime;
import java.util.*;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContestMemberService {
    private final ContestMapper contests;
    private final ContestRegisterRelMapper registrations;
    private final UserInfoMapper users;
    private final ResourcePermissionMapper permissions;
    private final PermissionService permissionService;
    private final UserRoleMapper roles;
    private final UserRoleRelMapper userRoles;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final AccountCreationService accountCreationService;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private Contest requireWrite(Long contestId) {
        Long actor = AuthContexts.requiredUserId();
        Contest contest = contests.selectById(contestId);
        if (contest == null) throw new NotFoundException("比赛不存在");
        permissionService.assertPermission(actor, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
        return contest;
    }

    private UserInfo findUser(String username) {
        // Let the database apply the same username collation used at login.
        UserInfo user;
        try {
            user = users.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUsername, username.strip()));
        } catch (org.apache.ibatis.exceptions.TooManyResultsException e) {
            throw new BadRequestException("存在同名历史账号，请联系站点管理员处理：" + username.strip());
        }
        if (user == null) throw new BadRequestException("账号不存在：" + username.strip());
        return user;
    }

    private UserInfo resolveUser(Long userId, String username) {
        if (userId == null) {
            if (username == null || username.isBlank()) throw new BadRequestException("请选择账号");
            return findUser(username);
        }
        UserInfo user = users.selectById(userId);
        if (user == null) throw new BadRequestException("账号不存在");
        if (username != null && !username.isBlank() && !user.getUsername().equals(username.strip()))
            throw new BadRequestException("账号信息已变化，请重新选择");
        return user;
    }

    public List<ContestMembersVO.AccountOption> searchAccounts(Long contestId, String query) {
        requireWrite(contestId);
        String keyword = query == null ? "" : query.strip();
        if (keyword.isEmpty()) return List.of();
        if (keyword.length() > 100) throw new BadRequestException("搜索内容最多 100 字");
        Long uid = null;
        try { if (keyword.matches("#?[0-9]+")) uid = Long.valueOf(keyword.replace("#", "")); }
        catch (NumberFormatException ignored) { /* Still allow a long numeric username. */ }
        Map<Long, UserInfo> found = new LinkedHashMap<>();
        if (uid != null) {
            UserInfo exact = users.selectById(uid);
            if (exact != null) found.put(exact.getId(), exact);
        }
        var matches = users.selectList(new LambdaQueryWrapper<UserInfo>()
                .select(UserInfo::getId, UserInfo::getUsername, UserInfo::getNickname, UserInfo::getIsEnabled)
                .like(UserInfo::getUsername, keyword).orderByAsc(UserInfo::getId).last("LIMIT 20"));
        matches.sort(Comparator.comparing(u -> !u.getUsername().equalsIgnoreCase(keyword)));
        matches.forEach(u -> found.putIfAbsent(u.getId(), u));
        return found.values().stream().limit(20).map(u -> new ContestMembersVO.AccountOption(
                u.getId(), u.getUsername(), u.getNickname(), !Boolean.FALSE.equals(u.getIsEnabled()))).toList();
    }

    public ContestMembersVO list(Long contestId) {
        Contest contest = requireWrite(contestId);
        var admins = permissions.selectPermissionsWithUserInfo("CONTEST", contestId).stream()
                .filter(p -> "WRITE".equals(p.getPermission())).toList();
        List<ContestRegisterRel> regs = registrations.selectList(new LambdaQueryWrapper<ContestRegisterRel>()
                .eq(ContestRegisterRel::getContestId, contestId).orderByAsc(ContestRegisterRel::getId));
        Map<Long, UserInfo> byId = new HashMap<>();
        var userIds = regs.stream().map(ContestRegisterRel::getUserId).filter(Objects::nonNull).toList();
        if (!userIds.isEmpty()) {
            users.selectBatchIds(userIds)
                    .forEach(u -> byId.put(u.getId(), u));
        }
        var participants = regs.stream().map(r -> {
            UserInfo u = byId.get(r.getUserId());
            boolean owned = u != null && Objects.equals(u.getTemporaryContestId(), contestId)
                    && userRoleService.getCapabilities(u.getId()).isEmpty();
            String status = u == null ? "UNGENERATED" : Boolean.FALSE.equals(u.getIsEnabled()) ? "DISABLED"
                    : owned ? "GENERATED" : "LINKED";
            return new ContestMembersVO.Participant(r.getId(), u == null ? null : u.getId(),
                    u == null ? null : u.getUsername(), u == null ? null : u.getNickname(),
                    r.getDisplayName(), Boolean.TRUE.equals(r.getIsStarred()), Boolean.TRUE.equals(r.getIsManaged()),
                    r.getStudentNo(), r.getCollege(), r.getClassName(), r.getContactEmail(), r.getNotes(), status, owned);
        }).toList();
        return new ContestMembersVO(contest.getCreatedByUserId(), admins, participants);
    }

    @Transactional
    public void addAdministrator(Long contestId, String username) {
        addAdministrator(contestId, null, username);
    }

    @Transactional
    public void addAdministrator(Long contestId, Long userId, String username) {
        requireWrite(contestId);
        permissionService.grantPermission(AuthContexts.requiredUserId(), ResourceType.CONTEST,
                contestId, resolveUser(userId, username).getId(), PermissionOp.WRITE);
    }

    @Transactional
    public void removeAdministrator(Long contestId, Long userId) {
        Contest contest = requireWrite(contestId);
        if (Objects.equals(userId, contest.getCreatedByUserId())) {
            throw new BadRequestException("不能移除比赛创建者的管理权限");
        }
        if (Objects.equals(userId, AuthContexts.requiredUserId())) {
            throw new BadRequestException("不能移除自己的管理权限");
        }
        permissionService.revokePermission(AuthContexts.requiredUserId(), ResourceType.CONTEST,
                contestId, userId, PermissionOp.WRITE);
    }

    /** Validate the entire import before making changes; a failed batch changes nothing. */
    @Transactional
    public int upsertParticipants(Long contestId, ContestMembersDTO.Batch batch) {
        requireWrite(contestId);
        contests.lockById(contestId);
        List<ContestRegisterRel> plans = new ArrayList<>();
        Set<Long> seenUsers = new HashSet<>(), seenRows = new HashSet<>();
        Set<String> seenStudents = new HashSet<>();
        for (int i = 0; i < batch.participants().size(); i++) {
            var entry = batch.participants().get(i);
            try {
                UserInfo user = entry.userId() == null && (entry.username() == null || entry.username().isBlank())
                        ? null : resolveUser(entry.userId(), entry.username());
                if (user != null && !seenUsers.add(user.getId())) throw new BadRequestException("账号重复：" + user.getUsername());
                String student = entry.studentNo() == null ? null : entry.studentNo().strip();
                if (student != null && !student.isBlank() && !seenStudents.add(student.toLowerCase(Locale.ROOT)))
                    throw new BadRequestException("学号重复：" + student);
                ContestRegisterRel reg = entry.id() == null ? null : registrations.selectById(entry.id());
                if (entry.id() != null && (reg == null || !contestId.equals(reg.getContestId())))
                    throw new BadRequestException("参赛记录不存在");
                ContestRegisterRel byUser = user == null ? null : registrations.selectOne(new LambdaQueryWrapper<ContestRegisterRel>()
                        .eq(ContestRegisterRel::getContestId, contestId).eq(ContestRegisterRel::getUserId, user.getId()));
                ContestRegisterRel byStudent = student == null || student.isBlank() ? null : registrations.selectOne(new LambdaQueryWrapper<ContestRegisterRel>()
                        .eq(ContestRegisterRel::getContestId, contestId).eq(ContestRegisterRel::getStudentNo, student));
                for (var match : Arrays.asList(byUser, byStudent)) {
                    if (match == null) continue;
                    if (reg != null && !Objects.equals(reg.getId(), match.getId()))
                        throw new BadRequestException("账号与学号对应不同选手");
                    reg = match;
                }
                if (reg == null) {
                    if (user == null && (student == null || student.isBlank()) && registrations.selectCount(new LambdaQueryWrapper<ContestRegisterRel>()
                            .eq(ContestRegisterRel::getContestId, contestId).eq(ContestRegisterRel::getDisplayName, entry.displayName().strip())) > 0)
                        throw new BadRequestException("已有同名选手，请填写学号或在名单中编辑");
                    reg = new ContestRegisterRel().setContestId(contestId).setJoinedAt(LocalDateTime.now()).setIsStarred(false);
                } else if (!seenRows.add(reg.getId())) throw new BadRequestException("同一选手在本批次出现多次");
                if (user != null) {
                    if (reg.getUserId() != null && !reg.getUserId().equals(user.getId()))
                        throw new BadRequestException("该选手已关联其他账号");
                    reg.setUserId(user.getId());
                }
                reg.setDisplayName(entry.displayName().strip()).setIsManaged(true);
                if (entry.isStarred() != null) reg.setIsStarred(entry.isStarred());
                if (student != null) reg.setStudentNo(student);
                if (entry.college() != null) reg.setCollege(entry.college().strip());
                if (entry.className() != null) reg.setClassName(entry.className().strip());
                if (entry.contactEmail() != null) reg.setContactEmail(entry.contactEmail().strip());
                if (entry.notes() != null) reg.setNotes(entry.notes().strip());
                plans.add(reg);
            } catch (BadRequestException e) {
                throw new BadRequestException("第 " + (i + 1) + " 行：" + e.getMessage());
            }
        }
        try {
            for (var reg : plans) {
                if (reg.getId() == null) registrations.insert(reg);
                else registrations.updateById(reg);
            }
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("账号或学号重复，请刷新名单后重试");
        }
        return plans.size();
    }

    private List<ContestRegisterRel> selected(Long contestId, List<Long> ids, boolean lock) {
        requireWrite(contestId);
        if (lock) contests.lockById(contestId);
        if (new HashSet<>(ids).size() != ids.size()) throw new BadRequestException("选手重复");
        List<ContestRegisterRel> rows = registrations.selectList(new LambdaQueryWrapper<ContestRegisterRel>()
                .eq(ContestRegisterRel::getContestId, contestId).in(ContestRegisterRel::getId, ids).orderByAsc(ContestRegisterRel::getId));
        if (rows.size() != ids.size()) throw new BadRequestException("部分选手已被移除，请刷新名单");
        return rows;
    }

    @Transactional
    public void batchAction(Long contestId, ContestMembersDTO.Selection dto) {
        var rows = selected(contestId, dto.ids(), true);
        for (var row : rows) {
            if ("remove".equals(dto.action())) registrations.deleteById(row.getId());
            else registrations.updateById(row.setIsStarred("star".equals(dto.action())).setIsManaged(true));
        }
    }

    private List<ContestMembersVO.AccountPlan> plan(List<ContestRegisterRel> rows, ContestMembersDTO.Generate dto) {
        List<ContestMembersVO.AccountPlan> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            String username = dto.prefix() + String.format(Locale.ROOT, "%0" + dto.digits() + "d", dto.start() + i);
            String error = row.getUserId() != null ? "已有账号" : users.selectCount(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUsername, username)) > 0 ? "账号已占用" : null;
            result.add(new ContestMembersVO.AccountPlan(row.getId(), row.getDisplayName(), username, error));
        }
        return result;
    }

    public List<ContestMembersVO.AccountPlan> previewAccounts(Long contestId, ContestMembersDTO.Generate dto) {
        return plan(selected(contestId, dto.ids(), false), dto);
    }

    private String randomPassword() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 12; i++) result.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return result.toString();
    }

    @Transactional
    public List<ContestMembersVO.Credential> generateAccounts(Long contestId, ContestMembersDTO.Generate dto) {
        var rows = selected(contestId, dto.ids(), true);
        var plan = plan(rows, dto);
        for (var item : plan) if (item.error() != null) throw new BadRequestException(item.username() + "：" + item.error());
        UserRole studentRole = roles.selectOne(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleCode, "STUDENT"));
        if (studentRole == null) throw new BadRequestException("学生角色尚未配置");
        List<ContestMembersVO.Credential> credentials = new ArrayList<>();
        try {
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i); var item = plan.get(i);
                String password = randomPassword();
                UserInfo user = new UserInfo().setUsername(item.username())
                        .setNickname(row.getDisplayName().substring(0, Math.min(64, row.getDisplayName().length())))
                        .setEmail("contest-" + UUID.randomUUID() + "@accounts.invalid")
                        .setAllowNicknameChange(false).setPassword(passwordEncoder.encode(password)).setTemporaryContestId(contestId).setIsEnabled(true);
                accountCreationService.insert(user);
                userRoles.insert(new UserRoleRel().setUserId(user.getId()).setRoleId(studentRole.getId()));
                registrations.updateById(row.setUserId(user.getId()).setIsManaged(true));
                credentials.add(new ContestMembersVO.Credential(row.getId(), row.getDisplayName(), item.username(), password));
            }
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("账号名已被占用，本批次未生成，请更换前缀或起始编号");
        }
        return credentials;
    }

    private Map<Long, UserInfo> ownedAccounts(Long contestId, List<ContestRegisterRel> rows) {
        Map<Long, UserInfo> result = new LinkedHashMap<>();
        for (var row : rows) {
            UserInfo user = row.getUserId() == null ? null : users.selectById(row.getUserId());
            if (user == null || !contestId.equals(user.getTemporaryContestId()) || !userRoleService.getCapabilities(user.getId()).isEmpty())
                throw new BadRequestException("只能管理本场生成的临时选手账号");
            result.put(row.getId(), user);
        }
        return result;
    }

    @Transactional
    public List<ContestMembersVO.Credential> resetAccounts(Long contestId, ContestMembersDTO.Accounts dto) {
        var rows = selected(contestId, dto.ids(), true);
        var accounts = ownedAccounts(contestId, rows);
        List<ContestMembersVO.Credential> result = new ArrayList<>();
        for (var row : rows) {
            UserInfo user = accounts.get(row.getId());
            String password = randomPassword();
            users.updateById(user.setPassword(passwordEncoder.encode(password)));
            result.add(new ContestMembersVO.Credential(row.getId(), row.getDisplayName(), user.getUsername(), password));
        }
        return result;
    }

    @Transactional
    public void setAccountsEnabled(Long contestId, ContestMembersDTO.Accounts dto) {
        if (dto.enabled() == null) throw new BadRequestException("缺少账号状态");
        var accounts = ownedAccounts(contestId, selected(contestId, dto.ids(), true));
        for (var user : accounts.values()) users.updateById(user.setIsEnabled(dto.enabled()));
    }

    @Transactional
    public void removeParticipant(Long contestId, Long userId) {
        requireWrite(contestId);
        registrations.delete(new LambdaQueryWrapper<ContestRegisterRel>()
                .eq(ContestRegisterRel::getContestId, contestId).eq(ContestRegisterRel::getUserId, userId));
    }
}

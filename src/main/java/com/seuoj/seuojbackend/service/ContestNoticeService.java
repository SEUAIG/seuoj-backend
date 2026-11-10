package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.AnnouncementTargetType;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestNoticeDTO;
import com.seuoj.seuojbackend.entity.Announcement;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestClarification;
import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.AnnouncementMapper;
import com.seuoj.seuojbackend.mapper.ContestClarificationMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.vo.contest.ContestNoticesVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 比赛公告与答疑。
 * 公告复用 announcement 表（target_type = CONTEST）；答疑在 contest_clarification。
 * 读：能看比赛的人都能读公告和公开答疑，登录用户另外能看到自己的提问；比赛管理者看到全部。
 * 写：公告、回复、删除需要比赛 WRITE 权限；提问需要登录且能看比赛，比赛结束后仍可提问。
 */
@Service
public class ContestNoticeService {

    /** 单人在一场比赛里最多挂着多少条未回复的提问，防止刷屏 */
    static final int MAX_PENDING_PER_USER = 10;

    private final ContestMapper contestMapper;
    private final AnnouncementMapper announcementMapper;
    private final ContestClarificationMapper clarificationMapper;
    private final ContestRegisterRelMapper registerRelMapper;
    private final ProblemMapper problemMapper;
    private final UserInfoMapper userInfoMapper;
    private final PermissionService permissionService;

    public ContestNoticeService(ContestMapper contestMapper,
                                AnnouncementMapper announcementMapper,
                                ContestClarificationMapper clarificationMapper,
                                ContestRegisterRelMapper registerRelMapper,
                                ProblemMapper problemMapper,
                                UserInfoMapper userInfoMapper,
                                PermissionService permissionService) {
        this.contestMapper = contestMapper;
        this.announcementMapper = announcementMapper;
        this.clarificationMapper = clarificationMapper;
        this.registerRelMapper = registerRelMapper;
        this.problemMapper = problemMapper;
        this.userInfoMapper = userInfoMapper;
        this.permissionService = permissionService;
    }

    // ---------------------------------------------------------------- 读

    public ContestNoticesVO getNotices(Long contestId) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.userIdOrNull();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);
        boolean canManage = canManage(userId, contest.getId());

        List<ContestNoticesVO.Announcement> announcements = announcementMapper.selectList(
                        new LambdaQueryWrapper<Announcement>()
                                .eq(Announcement::getTargetType, AnnouncementTargetType.CONTEST.name())
                                .eq(Announcement::getTargetId, contest.getId())
                                .orderByDesc(Announcement::getIsPinned)
                                .orderByDesc(Announcement::getCreatedAt)
                                .orderByDesc(Announcement::getId))
                .stream()
                .map(a -> new ContestNoticesVO.Announcement(a.getId(), a.getTitle(), a.getContent(),
                        Boolean.TRUE.equals(a.getIsPinned()), a.getCreatedAt(), a.getUpdatedAt()))
                .toList();

        LambdaQueryWrapper<ContestClarification> q = new LambdaQueryWrapper<ContestClarification>()
                .eq(ContestClarification::getContestId, contest.getId());
        if (!canManage) {
            if (userId == null) {
                q.eq(ContestClarification::getIsPublic, true);
            } else {
                q.and(w -> w.eq(ContestClarification::getIsPublic, true)
                        .or().eq(ContestClarification::getAskerUserId, userId));
            }
        }
        q.orderByDesc(ContestClarification::getCreatedAt).orderByDesc(ContestClarification::getId);
        List<ContestClarification> clars = clarificationMapper.selectList(q);

        Map<Long, String> pidById = pidsOf(clars.stream().map(ContestClarification::getProblemId).toList());
        Map<Long, UserInfo> askers = canManage
                ? usersOf(clars.stream().map(ContestClarification::getAskerUserId).toList())
                : Collections.emptyMap();
        Map<Long, String> rosterNames = canManage
                ? rosterNamesOf(contest.getId(), askers.keySet())
                : Collections.emptyMap();

        List<ContestNoticesVO.Clarification> clarifications = clars.stream().map(c -> {
            String askerName = null;
            String askerUsername = null;
            if (canManage) {
                UserInfo u = askers.get(c.getAskerUserId());
                askerUsername = u != null ? u.getUsername() : null;
                askerName = firstNonBlank(rosterNames.get(c.getAskerUserId()),
                        u != null ? u.getNickname() : null, askerUsername);
            }
            return new ContestNoticesVO.Clarification(c.getId(), pidById.get(c.getProblemId()), c.getQuestion(),
                    c.getAnswer(), Boolean.TRUE.equals(c.getIsPublic()),
                    userId != null && userId.equals(c.getAskerUserId()),
                    c.getCreatedAt(), c.getAnsweredAt(), askerName, askerUsername);
        }).toList();

        return new ContestNoticesVO(announcements, clarifications, canManage);
    }

    // ---------------------------------------------------------------- 公告

    public Long createAnnouncement(Long contestId, ContestNoticeDTO.Announcement dto) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        assertManage(userId, contest.getId());
        LocalDateTime now = LocalDateTime.now();
        Announcement a = new Announcement()
                .setTargetType(AnnouncementTargetType.CONTEST.name())
                .setTargetId(contest.getId())
                .setTitle(dto.title().trim())
                .setContent(dto.content() == null || dto.content().isBlank() ? null : dto.content())
                .setIsPinned(Boolean.TRUE.equals(dto.isPinned()))
                .setCreatedByUserId(userId)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        announcementMapper.insert(a);
        return a.getId();
    }

    public void updateAnnouncement(Long contestId, Long announcementId, ContestNoticeDTO.Announcement dto) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        assertManage(userId, contest.getId());
        Announcement a = loadAnnouncement(contest.getId(), announcementId);
        a.setTitle(dto.title().trim())
                .setContent(dto.content() == null || dto.content().isBlank() ? null : dto.content())
                .setIsPinned(Boolean.TRUE.equals(dto.isPinned()))
                .setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(a);
    }

    public void deleteAnnouncement(Long contestId, Long announcementId) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        assertManage(userId, contest.getId());
        loadAnnouncement(contest.getId(), announcementId);
        announcementMapper.deleteById(announcementId);
    }

    // ---------------------------------------------------------------- 答疑

    public Long ask(Long contestId, ContestNoticeDTO.Ask dto) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);
        boolean manage = canManage(userId, contest.getId());

        Long problemId = null;
        String pid = blankToNull(dto.problemPid());
        if (pid != null) {
            if (!manage && LocalDateTime.now().isBefore(contest.getStartTime())) {
                throw new BadRequestException("比赛开始前只能提整场比赛的问题");
            }
            problemId = problemIdInContest(contest.getId(), pid);
        }

        if (!manage) {
            Long pending = clarificationMapper.selectCount(new LambdaQueryWrapper<ContestClarification>()
                    .eq(ContestClarification::getContestId, contest.getId())
                    .eq(ContestClarification::getAskerUserId, userId)
                    .isNull(ContestClarification::getAnswer));
            if (pending != null && pending >= MAX_PENDING_PER_USER) {
                throw new BadRequestException("你还有 " + pending + " 条提问未回复，请等回复后再问");
            }
        }

        ContestClarification c = new ContestClarification()
                .setContestId(contest.getId())
                .setProblemId(problemId)
                .setAskerUserId(userId)
                .setQuestion(dto.question().trim())
                .setIsPublic(false)
                .setCreatedAt(LocalDateTime.now());
        clarificationMapper.insert(c);
        return c.getId();
    }

    /** 回复或修改回复；is_public 决定是否让所有人看到 */
    public void answer(Long contestId, Long clarificationId, ContestNoticeDTO.Answer dto) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        assertManage(userId, contest.getId());
        ContestClarification c = loadClarification(contest.getId(), clarificationId);
        c.setAnswer(dto.answer().trim())
                .setIsPublic(Boolean.TRUE.equals(dto.isPublic()))
                .setAnsweredByUserId(userId)
                .setAnsweredAt(LocalDateTime.now());
        clarificationMapper.updateById(c);
    }

    public void deleteClarification(Long contestId, Long clarificationId) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        assertManage(userId, contest.getId());
        loadClarification(contest.getId(), clarificationId);
        clarificationMapper.deleteById(clarificationId);
    }

    // ---------------------------------------------------------------- 辅助

    private Contest loadContest(Long contestId) {
        if (contestId == null) {
            throw new BadRequestException("contest_id 不能为空");
        }
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }
        return contest;
    }

    private boolean canManage(Long userId, Long contestId) {
        return userId != null && permissionService.hasPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }

    private void assertManage(Long userId, Long contestId) {
        permissionService.assertPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }

    private Announcement loadAnnouncement(Long contestId, Long announcementId) {
        Announcement a = announcementId == null ? null : announcementMapper.selectById(announcementId);
        if (a == null || !AnnouncementTargetType.CONTEST.name().equals(a.getTargetType())
                || !contestId.equals(a.getTargetId())) {
            throw new NotFoundException("公告不存在");
        }
        return a;
    }

    private ContestClarification loadClarification(Long contestId, Long clarificationId) {
        ContestClarification c = clarificationId == null ? null : clarificationMapper.selectById(clarificationId);
        if (c == null || !contestId.equals(c.getContestId())) {
            throw new NotFoundException("提问不存在");
        }
        return c;
    }

    private Long problemIdInContest(Long contestId, String pid) {
        List<ContestProblemOverviewVO> problems = contestMapper.selectContestProblems(contestId);
        boolean inContest = problems != null && problems.stream().anyMatch(p -> pid.equals(p.getPid()));
        Problem problem = inContest
                ? problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, pid))
                : null;
        if (problem == null) {
            throw new BadRequestException("题目不在本场比赛中");
        }
        return problem.getId();
    }

    private Map<Long, String> pidsOf(Collection<Long> problemIds) {
        List<Long> ids = problemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        return problemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Problem::getPid, (a, b) -> a));
    }

    private Map<Long, UserInfo> usersOf(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        return userInfoMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserInfo::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> rosterNamesOf(Long contestId, Collection<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return registerRelMapper.selectList(new LambdaQueryWrapper<ContestRegisterRel>()
                        .eq(ContestRegisterRel::getContestId, contestId)
                        .in(ContestRegisterRel::getUserId, userIds))
                .stream()
                .filter(r -> r.getUserId() != null && r.getDisplayName() != null && !r.getDisplayName().isBlank())
                .collect(Collectors.toMap(ContestRegisterRel::getUserId, ContestRegisterRel::getDisplayName, (a, b) -> a));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

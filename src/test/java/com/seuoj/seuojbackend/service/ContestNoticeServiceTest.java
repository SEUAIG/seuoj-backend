package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.AnnouncementTargetType;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestNoticeDTO;
import com.seuoj.seuojbackend.entity.Announcement;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestClarification;
import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.AnnouncementMapper;
import com.seuoj.seuojbackend.mapper.ContestClarificationMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.vo.contest.ContestNoticesVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContestNoticeServiceTest {
    private static final long CONTEST = 5L, ADMIN = 1L, ALICE = 2L, BOB = 3L;

    private final ContestMapper contests = mock(ContestMapper.class);
    private final AnnouncementMapper announcements = mock(AnnouncementMapper.class);
    private final ContestClarificationMapper clars = mock(ContestClarificationMapper.class);
    private final ContestRegisterRelMapper roster = mock(ContestRegisterRelMapper.class);
    private final ProblemMapper problems = mock(ProblemMapper.class);
    private final UserInfoMapper users = mock(UserInfoMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final ContestNoticeService service = new ContestNoticeService(
            contests, announcements, clars, roster, problems, users, permissions);

    private final Contest contest = new Contest();

    @BeforeEach
    void setUp() {
        contest.setId(CONTEST);
        contest.setStartTime(LocalDateTime.now().minusHours(1));
        contest.setEndTime(LocalDateTime.now().plusHours(2));
        when(contests.selectById(CONTEST)).thenReturn(contest);
        // 所有人都能读；只有 ADMIN 能写
        when(permissions.hasPermission(any(), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ))).thenReturn(true);
        when(permissions.hasPermission(eq(ADMIN), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.WRITE))).thenReturn(true);
        doAnswer(inv -> {
            if (!permissions.hasPermission(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3))) {
                throw new ForbiddenException("无权操作");
            }
            return null;
        }).when(permissions).assertPermission(any(), any(), any(), any());

        ContestProblemOverviewVO b = new ContestProblemOverviewVO();
        b.setPid("P0002");
        b.setSortOrder(2);
        when(contests.selectContestProblems(CONTEST)).thenReturn(List.of(b));
        Problem p = new Problem();
        p.setId(42L);
        p.setPid("P0002");
        when(problems.selectOne(any())).thenReturn(p);
        when(problems.selectBatchIds(any())).thenReturn(List.of(p));
        when(clars.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    private void login(long userId) {
        UserContextHolder.set(UserContext.of(userId, AuthStatus.AUTHENTICATED));
    }

    @Test
    void guestCannotAsk() {
        UserContextHolder.set(UserContext.guest());
        assertThrows(AuthorizationException.class,
                () -> service.ask(CONTEST, new ContestNoticeDTO.Ask(null, "题意？")));
        verify(clars, never()).insert(any(ContestClarification.class));
    }

    @Test
    void participantAsksPrivatelyAboutAProblem() {
        login(ALICE);
        service.ask(CONTEST, new ContestNoticeDTO.Ask("P0002", "  n 的上限是多少？  "));
        ArgumentCaptor<ContestClarification> saved = ArgumentCaptor.forClass(ContestClarification.class);
        verify(clars).insert(saved.capture());
        assertEquals(42L, saved.getValue().getProblemId());
        assertEquals(ALICE, saved.getValue().getAskerUserId());
        assertEquals("n 的上限是多少？", saved.getValue().getQuestion());
        assertFalse(saved.getValue().getIsPublic());
    }

    @Test
    void askingAfterContestEndIsAllowed() {
        contest.setStartTime(LocalDateTime.now().minusHours(5));
        contest.setEndTime(LocalDateTime.now().minusHours(1));
        login(ALICE);
        service.ask(CONTEST, new ContestNoticeDTO.Ask("P0002", "赛后还能问吗？"));
        verify(clars).insert(any(ContestClarification.class));
    }

    @Test
    void problemOutsideContestIsRejected() {
        login(ALICE);
        assertThrows(BadRequestException.class,
                () -> service.ask(CONTEST, new ContestNoticeDTO.Ask("P9999", "?")));
    }

    @Test
    void problemQuestionBeforeStartIsRejectedForParticipants() {
        contest.setStartTime(LocalDateTime.now().plusHours(1));
        login(ALICE);
        assertThrows(BadRequestException.class,
                () -> service.ask(CONTEST, new ContestNoticeDTO.Ask("P0002", "?")));
        service.ask(CONTEST, new ContestNoticeDTO.Ask(null, "几点开始？"));
        verify(clars).insert(any(ContestClarification.class));
    }

    @Test
    void tooManyPendingQuestionsAreRejected() {
        login(ALICE);
        when(clars.selectCount(any())).thenReturn((long) ContestNoticeService.MAX_PENDING_PER_USER);
        assertThrows(BadRequestException.class,
                () -> service.ask(CONTEST, new ContestNoticeDTO.Ask(null, "?")));
    }

    @Test
    void onlyManagersAnswer() {
        ContestClarification c = new ContestClarification().setId(9L).setContestId(CONTEST).setAskerUserId(ALICE);
        when(clars.selectById(9L)).thenReturn(c);

        login(BOB);
        assertThrows(ForbiddenException.class,
                () -> service.answer(CONTEST, 9L, new ContestNoticeDTO.Answer("见题面", true)));

        login(ADMIN);
        service.answer(CONTEST, 9L, new ContestNoticeDTO.Answer(" 见题面 ", true));
        verify(clars).updateById(c);
        assertEquals("见题面", c.getAnswer());
        assertTrue(c.getIsPublic());
        assertEquals(ADMIN, c.getAnsweredByUserId());
        assertNotNull(c.getAnsweredAt());
    }

    @Test
    void clarificationFromAnotherContestIsNotFound() {
        when(clars.selectById(9L)).thenReturn(new ContestClarification().setId(9L).setContestId(99L));
        login(ADMIN);
        assertThrows(NotFoundException.class,
                () -> service.answer(CONTEST, 9L, new ContestNoticeDTO.Answer("x", false)));
        assertThrows(NotFoundException.class, () -> service.deleteClarification(CONTEST, 9L));
    }

    @Test
    void announcementOfAnotherTargetIsNotFound() {
        Announcement classNotice = new Announcement().setId(7L)
                .setTargetType(AnnouncementTargetType.CLASS.name()).setTargetId(CONTEST);
        when(announcements.selectById(7L)).thenReturn(classNotice);
        login(ADMIN);
        assertThrows(NotFoundException.class, () -> service.updateAnnouncement(CONTEST, 7L,
                new ContestNoticeDTO.Announcement("t", null, false)));
        assertThrows(NotFoundException.class, () -> service.deleteAnnouncement(CONTEST, 7L));
        verify(announcements, never()).updateById(any(Announcement.class));
        verify(announcements, never()).deleteById(anyLong());
    }

    @Test
    void onlyManagersPostAnnouncements() {
        login(BOB);
        assertThrows(ForbiddenException.class, () -> service.createAnnouncement(CONTEST,
                new ContestNoticeDTO.Announcement("B 题数据范围更正", "n ≤ 2e5", true)));

        login(ADMIN);
        service.createAnnouncement(CONTEST, new ContestNoticeDTO.Announcement(" B 题数据范围更正 ", "n ≤ 2e5", true));
        ArgumentCaptor<Announcement> saved = ArgumentCaptor.forClass(Announcement.class);
        verify(announcements).insert(saved.capture());
        assertEquals(AnnouncementTargetType.CONTEST.name(), saved.getValue().getTargetType());
        assertEquals(CONTEST, saved.getValue().getTargetId());
        assertEquals("B 题数据范围更正", saved.getValue().getTitle());
        assertTrue(saved.getValue().getIsPinned());
    }

    private List<ContestClarification> sampleClars() {
        return List.of(
                new ContestClarification().setId(1L).setContestId(CONTEST).setAskerUserId(ALICE)
                        .setProblemId(42L).setQuestion("q1").setIsPublic(false),
                new ContestClarification().setId(2L).setContestId(CONTEST).setAskerUserId(BOB)
                        .setQuestion("q2").setAnswer("a2").setIsPublic(true));
    }

    @Test
    void participantSeesNoAskerIdentity() {
        when(announcements.selectList(any())).thenReturn(List.of());
        when(clars.selectList(any())).thenReturn(sampleClars());
        login(ALICE);
        ContestNoticesVO vo = service.getNotices(CONTEST);
        assertFalse(vo.canManage());
        ContestNoticesVO.Clarification mine = vo.clarifications().get(0);
        assertTrue(mine.mine());
        assertEquals("P0002", mine.problemPid());
        assertNull(mine.askerName());
        ContestNoticesVO.Clarification other = vo.clarifications().get(1);
        assertFalse(other.mine());
        assertNull(other.askerName());
        assertNull(other.askerUsername());
        verify(users, never()).selectBatchIds(any());
    }

    @Test
    void managerSeesRosterNameFirst() {
        when(announcements.selectList(any())).thenReturn(List.of());
        when(clars.selectList(any())).thenReturn(sampleClars());
        UserInfo alice = new UserInfo();
        alice.setId(ALICE);
        alice.setUsername("213240001");
        alice.setNickname("alice");
        UserInfo bob = new UserInfo();
        bob.setId(BOB);
        bob.setUsername("bob");
        when(users.selectBatchIds(any())).thenReturn(List.of(alice, bob));
        ContestRegisterRel rel = new ContestRegisterRel();
        rel.setUserId(ALICE);
        rel.setDisplayName("张三");
        when(roster.selectList(any())).thenReturn(List.of(rel));

        login(ADMIN);
        ContestNoticesVO vo = service.getNotices(CONTEST);
        assertTrue(vo.canManage());
        assertEquals("张三", vo.clarifications().get(0).askerName());
        assertEquals("213240001", vo.clarifications().get(0).askerUsername());
        assertEquals("bob", vo.clarifications().get(1).askerName());
    }

    @Test
    void privateContestStillRequiresReadPermission() {
        when(permissions.hasPermission(eq(BOB), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ))).thenReturn(false);
        login(BOB);
        assertThrows(ForbiddenException.class, () -> service.getNotices(CONTEST));
        assertThrows(ForbiddenException.class,
                () -> service.ask(CONTEST, new ContestNoticeDTO.Ask(null, "?")));
    }
}

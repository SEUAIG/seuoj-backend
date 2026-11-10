package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.service.contest.ContestRankingStrategy;
import com.seuoj.seuojbackend.service.contest.ContestRankingStrategyFactory;
import com.seuoj.seuojbackend.vo.contest.ContestSubmissionPageVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 隐藏统计时的榜单可见性，以及比赛提交列表只给选手看自己的 */
class ContestVisibilityTest {
    private static final long CONTEST = 13L, MANAGER = 1L, PLAYER = 2L;

    private final ContestMapper contests = mock(ContestMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final ContestRankingStrategyFactory strategies = mock(ContestRankingStrategyFactory.class);
    private final ContestService service = new ContestService(contests, null, mock(ContestRegisterRelMapper.class), null,
            null, null, mock(com.seuoj.seuojbackend.mapper.ProblemMapper.class), null, permissions,
            mock(UserRoleService.class), null, null, null, null, strategies, null);
    private final Contest contest = new Contest();

    @BeforeEach
    void setUp() {
        contest.setId(CONTEST);
        contest.setRuleType("ACM");
        contest.setHideStatistics(true);
        when(contests.selectById(CONTEST)).thenReturn(contest);
        when(permissions.hasPermission(any(), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ))).thenReturn(true);
        when(permissions.hasPermission(eq(MANAGER), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.WRITE))).thenReturn(true);
        when(contests.selectContestProblems(CONTEST)).thenReturn(List.of());
        when(contests.selectContestRegisteredUsers(CONTEST)).thenReturn(List.of());
        when(contests.selectContestFinishedSubmissions(CONTEST)).thenReturn(List.of());
        ContestRankingStrategy strategy = mock(ContestRankingStrategy.class);
        when(strategies.getStrategy("ACM")).thenReturn(strategy);
        when(strategy.computeStandings(any(), any(), any(), any(), any())).thenReturn(new ArrayList<>());
        when(contests.selectContestSubmissionPage(any(), eq(CONTEST), any(), any(), any(), any())).thenReturn(new Page<>(1, 20));
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    private void as(Long userId) {
        UserContextHolder.set(userId == null ? UserContext.guest() : UserContext.of(userId, AuthStatus.AUTHENTICATED));
    }

    private void running() {
        contest.setStartTime(LocalDateTime.now().minusHours(1));
        contest.setEndTime(LocalDateTime.now().plusHours(1));
    }

    @Test
    void hiddenStandingsAreForbiddenForPlayersDuringContest() {
        running();
        as(PLAYER);
        ForbiddenException e = assertThrows(ForbiddenException.class, () -> service.getContestStandings(CONTEST));
        assertEquals("本场比赛结束前不公开排名", e.getMessage());
    }

    @Test
    void managersStillSeeHiddenStandings() {
        running();
        as(MANAGER);
        assertNotNull(service.getContestStandings(CONTEST));
    }

    @Test
    void hiddenStandingsOpenAfterContest() {
        contest.setStartTime(LocalDateTime.now().minusHours(3));
        contest.setEndTime(LocalDateTime.now().minusHours(1));
        as(PLAYER);
        assertNotNull(service.getContestStandings(CONTEST));
    }

    @Test
    void visibleStandingsUnaffected() {
        running();
        contest.setHideStatistics(false);
        as(PLAYER);
        assertNotNull(service.getContestStandings(CONTEST));
    }

    @Test
    void playersOnlyListTheirOwnSubmissions() {
        running();
        as(PLAYER);
        service.getContestSubmissionPage(CONTEST, 1, 20);
        verify(contests).selectContestSubmissionPage(any(), eq(CONTEST), eq(PLAYER), isNull(), isNull(), isNull());
    }

    @Test
    void managersListAllSubmissions() {
        running();
        as(MANAGER);
        service.getContestSubmissionPage(CONTEST, 1, 20);
        verify(contests).selectContestSubmissionPage(any(), eq(CONTEST), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void guestsListNothing() {
        running();
        as(null);
        ContestSubmissionPageVO vo = service.getContestSubmissionPage(CONTEST, 1, 20);
        assertEquals(0L, vo.getTotal());
        verify(contests, never()).selectContestSubmissionPage(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void managersFilterByProblemResultAndUser() {
        running();
        as(MANAGER);
        service.getContestSubmissionPage(CONTEST, 1, 20, " P0002 ", "accepted", "alice");
        verify(contests).selectContestSubmissionPage(any(), eq(CONTEST), isNull(), eq("P0002"), eq("accepted"), eq("alice"));
    }

    @Test
    void playersCannotFilterOtherUsers() {
        running();
        as(PLAYER);
        service.getContestSubmissionPage(CONTEST, 1, 20, null, "rejected", "alice");
        verify(contests).selectContestSubmissionPage(any(), eq(CONTEST), eq(PLAYER), isNull(), eq("rejected"), isNull());
    }

    @Test
    void unknownResultFilterIsRejected() {
        running();
        as(MANAGER);
        assertThrows(com.seuoj.seuojbackend.exception.BadRequestException.class,
                () -> service.getContestSubmissionPage(CONTEST, 1, 20, null, "maybe", null));
    }
}

package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestSubmitDTO;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.service.contest.ContestRankingStrategy;
import com.seuoj.seuojbackend.service.contest.ContestRankingStrategyFactory;
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

/**
 * 提交时间窗口：管理者随时可交，赛后所有能看比赛的人可补题，开赛前选手不能交；
 * 榜单只统计比赛时间内的提交；题库里有题目管理权限的人能交非公开题。
 * 用空代码让请求在“通过了时间/权限检查”之后停在代码校验处，以此区分两种结果。
 */
class SubmissionWindowTest {
    private static final long CONTEST = 13L, MANAGER = 1L, PLAYER = 2L, PROBLEM = 154L;

    private final ContestMapper contests = mock(ContestMapper.class);
    private final ContestRegisterRelMapper registers = mock(ContestRegisterRelMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final ProblemService problemService = mock(ProblemService.class);
    private final ContestRankingStrategyFactory strategies = mock(ContestRankingStrategyFactory.class);
    private final ContestService contestService = new ContestService(contests, null, registers, null,
            null, null, mock(com.seuoj.seuojbackend.mapper.ProblemMapper.class), null, permissions,
            mock(UserRoleService.class), problemService, null, null, null,
            strategies, null);
    private final SubmissionService submissionService = new SubmissionService(null, null, null, null,
            null, null, null, mock(UserRoleService.class), permissions, problemService, null, null, null, null);

    private final Contest contest = new Contest();
    private final Problem problem = new Problem();

    @BeforeEach
    void setUp() {
        contest.setId(CONTEST);
        contest.setRuleType("ACM");
        when(contests.selectById(CONTEST)).thenReturn(contest);
        problem.setId(PROBLEM);
        problem.setPid("P0143");
        problem.setIsPublic(false);
        when(problemService.getProblemByPidOrThrow("P0143")).thenReturn(problem);
        ContestProblemOverviewVO p = new ContestProblemOverviewVO();
        p.setPid("P0143");
        p.setSortOrder(1);
        when(contests.selectContestProblems(CONTEST)).thenReturn(List.of(p));
        when(permissions.hasPermission(any(), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ))).thenReturn(true);
        when(permissions.hasPermission(eq(MANAGER), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.WRITE))).thenReturn(true);
        when(registers.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    private void window(int startOffsetHours, int endOffsetHours) {
        contest.setStartTime(LocalDateTime.now().plusHours(startOffsetHours));
        contest.setEndTime(LocalDateTime.now().plusHours(endOffsetHours));
    }

    private String contestSubmitAs(long userId) {
        UserContextHolder.set(UserContext.of(userId, AuthStatus.AUTHENTICATED));
        ContestSubmitDTO dto = new ContestSubmitDTO();
        dto.setPid("P0143");
        dto.setLanguage("C++17");
        dto.setCode(" ");
        return assertThrows(RuntimeException.class, () -> contestService.submitContestSolution(CONTEST, dto)).getMessage();
    }

    private static final String PASSED = "提交代码不能为空";

    @Test
    void managerCanSubmitBeforeStartWithoutRegistering() {
        window(1, 5);
        assertEquals(PASSED, contestSubmitAs(MANAGER));
    }

    @Test
    void playerCannotSubmitBeforeStart() {
        window(1, 5);
        assertEquals("比赛尚未开始", contestSubmitAs(PLAYER));
    }

    @Test
    void playerMustRegisterDuringContest() {
        window(-1, 3);
        assertEquals("请先报名比赛", contestSubmitAs(PLAYER));
        when(registers.selectCount(any())).thenReturn(1L);
        assertEquals(PASSED, contestSubmitAs(PLAYER));
    }

    @Test
    void anyoneWhoCanViewMayUpsolveAfterEnd() {
        window(-5, -1);
        assertEquals(PASSED, contestSubmitAs(PLAYER));
        verify(registers, never()).selectCount(any());
    }

    @Test
    void upsolvingStillNeedsContestReadPermission() {
        window(-5, -1);
        when(permissions.hasPermission(eq(PLAYER), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ))).thenReturn(false);
        doThrow(new ForbiddenException("无权操作")).when(permissions)
                .assertPermission(eq(PLAYER), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ));
        assertEquals("无权操作", contestSubmitAs(PLAYER));
    }

    @Test
    void standingsIgnoreSubmissionsOutsideContestTime() {
        window(-5, -1);
        UserContextHolder.set(UserContext.of(PLAYER, AuthStatus.AUTHENTICATED));
        Submission before = new Submission();
        before.setSubmitTime(contest.getStartTime().minusMinutes(1));
        Submission during = new Submission();
        during.setSubmitTime(contest.getStartTime().plusMinutes(30));
        Submission after = new Submission();
        after.setSubmitTime(contest.getEndTime().plusMinutes(1));
        when(contests.selectContestFinishedSubmissions(CONTEST)).thenReturn(List.of(before, during, after));
        when(contests.selectContestRegisteredUsers(CONTEST)).thenReturn(List.of());
        ContestRankingStrategy strategy = mock(ContestRankingStrategy.class);
        when(strategies.getStrategy("ACM")).thenReturn(strategy);
        when(strategy.computeStandings(any(), any(), any(), any(), any())).thenReturn(new java.util.ArrayList<>());

        contestService.getContestStandings(CONTEST);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Submission>> counted = ArgumentCaptor.forClass(List.class);
        verify(strategy).computeStandings(any(), any(), counted.capture(), any(), any());
        assertEquals(List.of(during), counted.getValue());
    }

    private String librarySubmitAs(long userId) {
        UserContextHolder.set(UserContext.of(userId, AuthStatus.AUTHENTICATED));
        SubmitDTO dto = new SubmitDTO();
        dto.setPid("P0143");
        dto.setLanguage("C++17");
        dto.setCode(" ");
        return assertThrows(RuntimeException.class, () -> submissionService.submit(dto)).getMessage();
    }

    @Test
    void problemManagerCanSubmitPrivateProblemFromLibrary() {
        when(permissions.hasPermission(eq(MANAGER), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.WRITE))).thenReturn(true);
        assertEquals(PASSED, librarySubmitAs(MANAGER));
        assertTrue(assertThrows(NotFoundException.class, () -> {
            UserContextHolder.set(UserContext.of(PLAYER, AuthStatus.AUTHENTICATED));
            SubmitDTO dto = new SubmitDTO();
            dto.setPid("P0143");
            dto.setLanguage("C++17");
            dto.setCode(" ");
            submissionService.submit(dto);
        }).getMessage().contains("P0143"));
    }

    @Test
    void blankCodeMessageIsTheMarker() {
        // 防止标记文案变化后上面的测试“误通过”
        window(-1, 3);
        when(registers.selectCount(any())).thenReturn(1L);
        assertThrows(BadRequestException.class, () -> {
            UserContextHolder.set(UserContext.of(PLAYER, AuthStatus.AUTHENTICATED));
            ContestSubmitDTO dto = new ContestSubmitDTO();
            dto.setPid("P0143");
            dto.setLanguage("C++17");
            dto.setCode(" ");
            contestService.submitContestSolution(CONTEST, dto);
        });
    }
}

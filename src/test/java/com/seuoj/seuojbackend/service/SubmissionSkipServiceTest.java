package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionSkipServiceTest {
    final SubmissionMapper submissions = mock(SubmissionMapper.class);
    final ContestSubmissionMapper contestSubmissions = mock(ContestSubmissionMapper.class);
    final ProblemMapper problems = mock(ProblemMapper.class);
    final AssignmentMapper assignments = mock(AssignmentMapper.class);
    final UserInfoMapper users = mock(UserInfoMapper.class);
    final PermissionService permissions = mock(PermissionService.class);
    final SubmissionSkipService skips = new SubmissionSkipService(submissions, contestSubmissions, problems, assignments, users, permissions);

    @BeforeEach void setup() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "skip-test");
        assistant.setCurrentNamespace("skip-test");
        for (Class<?> type : List.of(Submission.class, UserInfo.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, type);
    }

    private static Submission sub(long id, String no, long problemId, String status, String verdict, boolean skipped) {
        return new Submission().setId(id).setSubmissionNo(no).setProblemId(problemId).setUserId(7L)
                .setStatus(status).setVerdict(verdict).setIsSkipped(skipped);
    }

    private static SubmissionSkipService.SkipRequest req(boolean skipped, String... nos) {
        return new SubmissionSkipService.SkipRequest(List.of(nos), skipped, "雷同");
    }

    @Test void skippingAcceptedContestSubmissionRemovesItFromProblemCounts() {
        when(submissions.selectList(any())).thenReturn(List.of(
                sub(1, "a", 10, "Finished", "Accepted", false),
                sub(2, "b", 10, "Finished", "WrongAnswer", false),
                sub(3, "c", 11, "Pending", null, false)));
        when(contestSubmissions.findContestIdBySubmissionId(anyLong())).thenReturn(5L);
        when(permissions.hasPermission(1L, ResourceType.CONTEST, 5L, PermissionOp.WRITE)).thenReturn(true);

        var outcome = skips.skip(1L, req(true, "a", "b", "c"));

        assertEquals(3, outcome.changed());
        verify(submissions, times(3)).update(isNull(), any());
        verify(problems).adjustTotalCounts(10L, -2, -1);
        verify(problems).adjustTotalCounts(11L, -1, 0);
    }

    @Test void unskipRestoresCountsAndIgnoresAlreadyActiveRows() {
        when(submissions.selectList(any())).thenReturn(List.of(
                sub(1, "a", 10, "Finished", "Accepted", true),
                sub(2, "b", 10, "Finished", "Accepted", false)));
        when(contestSubmissions.findContestIdBySubmissionId(anyLong())).thenReturn(null);
        when(permissions.hasPermission(1L, ResourceType.PROBLEM, 10L, PermissionOp.WRITE)).thenReturn(true);

        var outcome = skips.skip(1L, req(false, "a", "b"));

        assertEquals(2, outcome.matched());
        assertEquals(1, outcome.changed());
        verify(problems).adjustTotalCounts(10L, 1, 1);
    }

    @Test void oneForbiddenSubmissionRejectsWholeBatch() {
        when(submissions.selectList(any())).thenReturn(List.of(
                sub(1, "a", 10, "Finished", "Accepted", false),
                sub(2, "b", 12, "Finished", "Accepted", false)));
        when(contestSubmissions.findContestIdBySubmissionId(anyLong())).thenReturn(null);
        when(permissions.hasPermission(1L, ResourceType.PROBLEM, 10L, PermissionOp.WRITE)).thenReturn(true);

        var e = assertThrows(ForbiddenException.class, () -> skips.skip(1L, req(true, "a", "b")));
        assertTrue(e.getMessage().contains("b"));
        verify(submissions, never()).update(any(), any());
        verify(problems, never()).adjustTotalCounts(anyLong(), anyInt(), anyInt());
    }

    @Test void classTeacherMaySkipAssignmentSubmission() {
        Submission s = sub(1, "a", 10, "Finished", "Accepted", false).setAssignmentId(30L);
        when(contestSubmissions.findContestIdBySubmissionId(anyLong())).thenReturn(null);
        when(submissions.selectList(any())).thenReturn(List.of(s));
        when(assignments.selectById(30L)).thenReturn(new Assignment().setClassId(40L));
        when(permissions.hasPermission(1L, ResourceType.CLASS, 40L, PermissionOp.WRITE)).thenReturn(true);

        assertEquals(1, skips.skip(1L, req(true, "a")).changed());
    }

    @Test void contestPermissionDoesNotFallBackToProblemPermission() {
        when(submissions.selectList(any())).thenReturn(List.of(sub(1, "a", 10, "Finished", "Accepted", false)));
        when(contestSubmissions.findContestIdBySubmissionId(1L)).thenReturn(5L);
        when(permissions.hasPermission(1L, ResourceType.PROBLEM, 10L, PermissionOp.WRITE)).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> skips.skip(1L, req(true, "a")));
    }

    @Test void missingSubmissionRejectsWholeBatch() {
        when(submissions.selectList(any())).thenReturn(List.of(sub(1, "a", 10, "Finished", "Accepted", false)));
        var e = assertThrows(BadRequestException.class, () -> skips.skip(1L, req(true, "a", "ghost")));
        assertTrue(e.getMessage().contains("ghost"));
        verify(submissions, never()).update(any(), any());
    }

    @Test void contestSkipRequiresContestWrite() {
        doThrow(new ForbiddenException("no")).when(permissions).assertPermission(2L, ResourceType.CONTEST, 5L, PermissionOp.WRITE);
        var dto = new SubmissionSkipService.ContestSkipRequest(null, "alice", true, null);
        assertThrows(ForbiddenException.class, () -> skips.skipInContest(2L, 5L, dto));
        verify(submissions, never()).selectList(any());
    }

    @Test void contestSkipByUsernameTakesAllTheirSubmissions() {
        when(users.selectList(any())).thenReturn(List.of(new UserInfo().setId(7L)));
        when(submissions.selectList(any())).thenReturn(List.of(
                sub(1, "a", 10, "Finished", "Accepted", false),
                sub(2, "b", 11, "Finished", "WrongAnswer", false)));
        var outcome = skips.skipInContest(1L, 5L, new SubmissionSkipService.ContestSkipRequest(null, "alice", true, "作弊"));
        assertEquals(2, outcome.changed());
        verify(problems).adjustTotalCounts(10L, -1, -1);
        verify(problems).adjustTotalCounts(11L, -1, 0);
    }

    @Test void contestSkipRejectsSubmissionsFromOtherContests() {
        when(submissions.selectList(any()))
                .thenReturn(List.of(sub(1, "a", 10, "Finished", "Accepted", false)))
                .thenReturn(List.of());
        var dto = new SubmissionSkipService.ContestSkipRequest(List.of("a"), null, true, null);
        assertThrows(BadRequestException.class, () -> skips.skipInContest(1L, 5L, dto));
        verify(submissions, never()).update(any(), any());
    }

    @Test void unknownContestUserIsReported() {
        when(users.selectList(any())).thenReturn(List.of());
        var dto = new SubmissionSkipService.ContestSkipRequest(null, "ghost", true, null);
        assertThrows(BadRequestException.class, () -> skips.skipInContest(1L, 5L, dto));
    }

    @Test void canSkipIsFalseForAnonymousOrUnknown() {
        assertFalse(skips.canSkip(null, "a"));
        assertFalse(skips.canSkip(1L, (Submission) null));
    }
}

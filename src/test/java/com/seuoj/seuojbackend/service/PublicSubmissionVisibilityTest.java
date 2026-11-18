package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ContestSubmissionMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionDetailMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import com.seuoj.seuojbackend.storage.CodeStorage;
import com.seuoj.seuojbackend.vo.submission.SubmissionResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 他人的非比赛提交：非作业、公开题、题目不在进行中的比赛里才能看，且看不到测试数据 */
class PublicSubmissionVisibilityTest {
    private static final long OWNER = 1L, VIEWER = 2L, TEACHER = 3L;

    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final SubmissionDetailMapper details = mock(SubmissionDetailMapper.class);
    private final ProblemMapper problems = mock(ProblemMapper.class);
    private final CodeStorage code = mock(CodeStorage.class);
    private final UserInfoMapper users = mock(UserInfoMapper.class);
    private final UserRoleService roles = mock(UserRoleService.class);
    private final ContestSubmissionMapper contestLinks = mock(ContestSubmissionMapper.class);
    private final SubmissionService service = new SubmissionService(submissions, details, problems, null,
            null, code, users, roles, null, null, null, null, contestLinks, null, null);

    private final Submission submission = new Submission();
    private final Problem problem = new Problem();

    @BeforeEach void setup() {
        submission.setId(10L);
        submission.setSubmissionNo("S1");
        submission.setUserId(OWNER);
        submission.setProblemId(20L);
        submission.setStatus("Finished");
        submission.setVerdict("WrongAnswer");
        problem.setId(20L);
        problem.setPid("P0001");
        problem.setIsPublic(true);
        when(submissions.selectOne(any())).thenReturn(submission);
        when(problems.selectById(20L)).thenReturn(problem);
        when(code.getCode("S1")).thenReturn("int main(){}");
        when(roles.isTeacherOrAdmin(TEACHER)).thenReturn(true);
        when(contestLinks.findContestIdBySubmissionId(10L)).thenReturn(null);

        JudgeResultDetailItem item = new JudgeResultDetailItem();
        item.setId(1);
        item.setIn("1 2");
        item.setAns("3");
        item.setOut("4");
        item.setSys("wrong answer expected 3");
        item.setTime(5L);
        item.setMem(1024L);
        item.setType(SubmissionVerdict.WRONG_ANSWER);
        item.setScore(0);
        SubmissionDetail detail = new SubmissionDetail();
        detail.setResultDetail(List.of(item));
        detail.setErrorDetail("judge message");
        when(details.selectById(10L)).thenReturn(detail);
    }

    @AfterEach void cleanup() { UserContextHolder.clear(); }

    private SubmissionResultVO viewAs(long userId) {
        UserContextHolder.set(UserContext.of(userId, AuthStatus.AUTHENTICATED));
        return service.getResult("S1");
    }

    @Test void othersSeeCodeAndPerCaseResultsWithoutTestData() {
        SubmissionResultVO vo = viewAs(VIEWER);
        assertEquals("int main(){}", vo.getCode());
        JudgeResultDetailItem item = vo.getResultDetail().get(0);
        assertEquals(5L, item.getTime());
        assertEquals(SubmissionVerdict.WRONG_ANSWER, item.getType());
        assertNull(item.getIn());
        assertNull(item.getAns());
        assertNull(item.getOut());
        assertNull(item.getSys());
        assertNull(vo.getErrorDetail());
        assertFalse(vo.getTestDataVisible());
    }

    @Test void othersSeeCompileErrors() {
        submission.setVerdict("CompileError");
        assertEquals("judge message", viewAs(VIEWER).getErrorDetail());
    }

    @Test void ownerAndTeacherKeepFullDetail() {
        assertEquals("1 2", viewAs(OWNER).getResultDetail().get(0).getIn());
        assertEquals("1 2", viewAs(TEACHER).getResultDetail().get(0).getIn());
        verify(submissions, never()).countRunningContestsUsingProblem(any(), any());
    }

    @Test void homeworkSubmissionsStayPrivate() {
        submission.setAssignmentId(7L);
        assertThrows(ForbiddenException.class, () -> viewAs(VIEWER));
    }

    @Test void nonPublicProblemsStayPrivate() {
        problem.setIsPublic(false);
        assertThrows(ForbiddenException.class, () -> viewAs(VIEWER));
    }

    @Test void problemsInRunningContestsStayPrivate() {
        when(submissions.countRunningContestsUsingProblem(eq(20L), any())).thenReturn(1L);
        assertThrows(ForbiddenException.class, () -> viewAs(VIEWER));
    }

    @Test void unfinishedJudgingShowsNoCases() {
        submission.setStatus("Judging");
        assertNull(viewAs(VIEWER).getResultDetail());
    }
}

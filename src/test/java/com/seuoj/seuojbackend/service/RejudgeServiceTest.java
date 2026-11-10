package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.*;
import com.seuoj.seuojbackend.storage.CodeStorage;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RejudgeServiceTest {
    final SubmissionMapper submissions = mock(SubmissionMapper.class);
    final SubmissionDetailMapper details = mock(SubmissionDetailMapper.class);
    final ProblemMapper problems = mock(ProblemMapper.class);
    final PermissionService permissions = mock(PermissionService.class);
    final SubmissionSkipService skips = mock(SubmissionSkipService.class);
    final RejudgeDispatcher dispatcher = mock(RejudgeDispatcher.class);
    final RejudgeService rejudge = new RejudgeService(submissions, details, problems, permissions, skips, dispatcher);

    @BeforeEach void setup() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "rejudge-test");
        assistant.setCurrentNamespace("rejudge-test");
        for (Class<?> type : List.of(Submission.class, SubmissionDetail.class, Problem.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, type);
    }

    private static Submission sub(long id, String no, String status, String verdict, boolean skipped, LocalDateTime updated) {
        return new Submission().setId(id).setSubmissionNo(no).setProblemId(10L).setLanguage("Cpp17")
                .setStatus(status).setVerdict(verdict).setIsSkipped(skipped).setUpdatedAt(updated);
    }

    private static final LocalDateTime OLD = LocalDateTime.now().minusDays(1);

    @SuppressWarnings("unchecked")
    @Test void resetsFinishedSubmissionsAndOnlyUncountsActiveAccepts() {
        when(submissions.selectList(any())).thenReturn(List.of(
                sub(1, "a", "Finished", "Accepted", false, OLD),
                sub(2, "b", "Finished", "Accepted", true, OLD),
                sub(3, "c", "Finished", "WrongAnswer", false, OLD)));

        var outcome = rejudge.rejudgeSelected(1L, new RejudgeService.SelectedRequest(List.of("a", "b", "c")));

        assertEquals(3, outcome.queued());
        assertEquals(0, outcome.busy());
        verify(skips).assertCanManage(eq(1L), anyList());
        verify(submissions).update(isNull(), any());
        verify(details).delete(any());
        verify(problems).adjustTotalCounts(10L, 0, -1);
        ArgumentCaptor<Collection<String>> queued = ArgumentCaptor.forClass(Collection.class);
        verify(dispatcher).enqueue(queued.capture());
        assertEquals(List.of("a", "b", "c"), List.copyOf(queued.getValue()));
    }

    @Test void submissionsBeingJudgedAreLeftAlone() {
        when(submissions.selectList(any())).thenReturn(List.of(
                sub(1, "a", "Running", null, false, LocalDateTime.now().minusSeconds(30)),
                sub(2, "b", "Running", null, false, OLD)));

        var outcome = rejudge.rejudgeSelected(1L, new RejudgeService.SelectedRequest(List.of("a", "b")));

        assertEquals(1, outcome.queued());
        assertEquals(1, outcome.busy());
        verify(dispatcher).enqueue(List.of("b"));
    }

    @Test void nothingToDoWhenEverythingIsBusy() {
        when(submissions.selectList(any())).thenReturn(List.of(sub(1, "a", "Pending", null, false, LocalDateTime.now())));
        var outcome = rejudge.rejudgeSelected(1L, new RejudgeService.SelectedRequest(List.of("a")));
        assertEquals(0, outcome.queued());
        verify(submissions, never()).update(any(), any());
        verify(dispatcher, never()).enqueue(any());
    }

    @Test void forbiddenSelectionChangesNothing() {
        when(submissions.selectList(any())).thenReturn(List.of(sub(1, "a", "Finished", "Accepted", false, OLD)));
        doThrow(new ForbiddenException("no")).when(skips).assertCanManage(eq(2L), anyList());
        assertThrows(ForbiddenException.class, () -> rejudge.rejudgeSelected(2L, new RejudgeService.SelectedRequest(List.of("a"))));
        verify(submissions, never()).update(any(), any());
        verify(dispatcher, never()).enqueue(any());
    }

    @Test void missingSubmissionIsReported() {
        when(submissions.selectList(any())).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> rejudge.rejudgeSelected(1L, new RejudgeService.SelectedRequest(List.of("ghost"))));
    }

    @Test void contestRejudgeRequiresContestWrite() {
        doThrow(new ForbiddenException("no")).when(permissions).assertPermission(2L, ResourceType.CONTEST, 5L, PermissionOp.WRITE);
        assertThrows(ForbiddenException.class, () -> rejudge.rejudgeInContest(2L, 5L, new RejudgeService.ContestRequest(null, "P1")));
        verify(submissions, never()).selectList(any());
    }

    @Test void contestWholeProblemTakesItsSubmissions() {
        when(problems.selectOne(any())).thenReturn(new Problem().setId(10L).setPid("P1"));
        when(submissions.selectList(any())).thenReturn(List.of(
                sub(1, "a", "Finished", "Accepted", false, OLD), sub(2, "b", "Finished", "WrongAnswer", false, OLD)));
        assertEquals(2, rejudge.rejudgeInContest(1L, 5L, new RejudgeService.ContestRequest(null, "P1")).queued());
    }

    @Test void problemRejudgeRequiresProblemWrite() {
        when(problems.selectOne(any())).thenReturn(new Problem().setId(10L).setPid("P1"));
        doThrow(new ForbiddenException("no")).when(permissions).assertPermission(2L, ResourceType.PROBLEM, 10L, PermissionOp.WRITE);
        assertThrows(ForbiddenException.class, () -> rejudge.rejudgeProblem(2L, "P1"));
        verify(submissions, never()).update(any(), any());
    }

    @Test void unknownProblemIsNotFound() {
        when(problems.selectOne(any())).thenReturn(null);
        assertThrows(com.seuoj.seuojbackend.exception.NotFoundException.class, () -> rejudge.rejudgeProblem(1L, "NOPE"));
    }

    @Test void tooManySubmissionsAreRejected() {
        when(problems.selectOne(any())).thenReturn(new Problem().setId(10L).setPid("P1"));
        List<Submission> many = new ArrayList<>();
        for (int i = 0; i <= RejudgeService.MAX_WHOLE_PROBLEM; i++) many.add(sub(i, "n" + i, "Finished", "WrongAnswer", false, OLD));
        when(submissions.selectList(any())).thenReturn(many);
        assertThrows(BadRequestException.class, () -> rejudge.rejudgeProblem(1L, "P1"));
        verify(submissions, never()).update(any(), any());
    }

    // ---- dispatcher

    @Test void dispatcherSendsPendingAndMarksRunning() {
        SubmissionMapper subs = mock(SubmissionMapper.class);
        ProblemMapper probs = mock(ProblemMapper.class);
        CodeStorage code = mock(CodeStorage.class);
        JudgeClient judge = mock(JudgeClient.class);
        RejudgeDispatcher d = new RejudgeDispatcher(subs, probs, code, judge, 2, 10);
        when(subs.selectOne(any())).thenReturn(sub(1, "a", "Pending", null, false, OLD));
        when(probs.selectById(10L)).thenReturn(new Problem().setId(10L).setPid("P1"));
        when(code.getCode("a")).thenReturn("int main(){}");

        d.dispatch("a");

        ArgumentCaptor<JudgeSubmissionRequest> sent = ArgumentCaptor.forClass(JudgeSubmissionRequest.class);
        verify(judge).submit(sent.capture());
        assertEquals("P1", sent.getValue().getPid());
        assertEquals("Cpp17", sent.getValue().getLanguage());
        verify(subs).update(isNull(), any());
    }

    @Test void dispatcherSkipsRowsNoLongerPending() {
        SubmissionMapper subs = mock(SubmissionMapper.class);
        JudgeClient judge = mock(JudgeClient.class);
        RejudgeDispatcher d = new RejudgeDispatcher(subs, mock(ProblemMapper.class), mock(CodeStorage.class), judge, 2, 10);
        when(subs.selectOne(any())).thenReturn(sub(1, "a", "Finished", "Accepted", false, OLD));
        d.dispatch("a");
        verify(judge, never()).submit(any());
    }

    @Test void dispatcherWaitsWhileJudgeIsBusy() throws Exception {
        SubmissionMapper subs = mock(SubmissionMapper.class);
        ProblemMapper probs = mock(ProblemMapper.class);
        CodeStorage code = mock(CodeStorage.class);
        JudgeClient judge = mock(JudgeClient.class);
        RejudgeDispatcher d = new RejudgeDispatcher(subs, probs, code, judge, 2, 10);
        // 前三次查询判题端满载，之后空出名额
        when(subs.selectCount(any())).thenReturn(2L, 2L, 3L, 1L);
        when(subs.selectOne(any())).thenReturn(sub(1, "a", "Pending", null, false, OLD));
        when(probs.selectById(10L)).thenReturn(new Problem().setId(10L).setPid("P1"));
        when(code.getCode("a")).thenReturn("x");

        d.enqueue(List.of("a"));
        verify(judge, timeout(2000)).submit(any());
        verify(subs, atLeast(4)).selectCount(any());
        d.stop();
    }
}

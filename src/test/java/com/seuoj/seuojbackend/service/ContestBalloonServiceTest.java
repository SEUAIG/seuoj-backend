package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestBalloonProblemRow;
import com.seuoj.seuojbackend.entity.ContestBalloon;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.mapper.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContestBalloonServiceTest {
    static final long CONTEST = 17L, USER = 7L, PROBLEM = 100L;

    final ContestBalloonMapper balloons = mock(ContestBalloonMapper.class);
    final ContestProblemRelMapper rels = mock(ContestProblemRelMapper.class);
    final ContestSubmissionMapper contestSubmissions = mock(ContestSubmissionMapper.class);
    final SubmissionMapper submissions = mock(SubmissionMapper.class);
    final ProblemMapper problems = mock(ProblemMapper.class);
    final PermissionService permissions = mock(PermissionService.class);
    final ContestRegisterRelMapper registers = mock(ContestRegisterRelMapper.class);
    final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    final ContestBalloonService service = new ContestBalloonService(
            balloons, rels, contestSubmissions, submissions, problems, permissions, registers, tx);

    @BeforeEach void setup() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "balloon-test");
        assistant.setCurrentNamespace("balloon-test");
        for (Class<?> type : List.of(ContestBalloon.class, ContestRegisterRel.class, ContestProblemRel.class, Submission.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, type);
        when(contestSubmissions.findContestIdBySubmissionId(anyLong())).thenReturn(CONTEST);
        when(submissions.selectById(anyLong())).thenAnswer(inv ->
                new Submission().setId(inv.getArgument(0)).setUserId(USER).setProblemId(PROBLEM));
        ContestBalloonProblemRow row = new ContestBalloonProblemRow();
        row.setProblemId(PROBLEM);
        row.setPid("P1001");
        when(balloons.selectProblems(CONTEST)).thenReturn(List.of(row));
    }

    private void registered(boolean yes) {
        when(registers.selectCount(any())).thenReturn(yes ? 1L : 0L);
    }

    private void manager(boolean yes) {
        when(permissions.hasPermission(USER, ResourceType.CONTEST, CONTEST, PermissionOp.WRITE)).thenReturn(yes);
    }

    private void existing(ContestBalloon balloon) {
        when(balloons.selectOne(any())).thenReturn(balloon);
    }

    @Test void firstValidAcceptedIsQueuedWithTheEffectiveColor() {
        registered(true);
        manager(false);
        existing(null);
        when(balloons.selectFirstValidAccepted(CONTEST, USER, PROBLEM)).thenReturn(55L);

        service.reconcileSubmission(55L);

        verify(balloons).insert(argThat((ContestBalloon b) ->
                b.getSubmissionId() == 55L && "PENDING".equals(b.getStatus()) && "#ef4444".equals(b.getColor())));
    }

    @Test void managersDoNotGetBalloons() {
        registered(true);
        manager(true);
        existing(null);

        service.reconcileSubmission(55L);

        verify(balloons, never()).selectFirstValidAccepted(anyLong(), anyLong(), anyLong());
        verify(balloons, never()).insert(any(ContestBalloon.class));
    }

    @Test void unregisteredUsersDoNotGetBalloons() {
        registered(false);
        existing(null);

        service.reconcileSubmission(55L);

        verify(balloons, never()).insert(any(ContestBalloon.class));
    }

    @Test void acceptedOutsideTheContestWindowIsNotQueued() {
        registered(true);
        manager(false);
        existing(null);
        // SQL 按比赛时间、skip、verdict 过滤后没有合格提交
        when(balloons.selectFirstValidAccepted(CONTEST, USER, PROBLEM)).thenReturn(null);

        service.reconcileSubmission(55L);

        verify(balloons, never()).insert(any(ContestBalloon.class));
        verify(balloons, never()).update(any(), any());
    }

    @Test void pendingBalloonIsWithdrawnWhenItsOnlyAcceptedIsRejudgedOrSkipped() {
        registered(true);
        manager(false);
        existing(new ContestBalloon().setId(9L).setSubmissionId(55L).setStatus("PENDING"));
        when(balloons.selectFirstValidAccepted(CONTEST, USER, PROBLEM)).thenReturn(null);

        service.reconcileSubmission(55L);

        verify(balloons).update(isNull(), any());
        verify(balloons, never()).insert(any(ContestBalloon.class));
    }

    @Test void deliveredBalloonIsKeptWhenItsAcceptedIsRevoked() {
        registered(true);
        manager(false);
        existing(new ContestBalloon().setId(9L).setSubmissionId(55L).setStatus("DELIVERED"));
        when(balloons.selectFirstValidAccepted(CONTEST, USER, PROBLEM)).thenReturn(null);

        service.reconcileSubmission(55L);

        verify(balloons, never()).update(any(), any());
    }

    @Test void balloonMovesToAnotherValidAcceptedInsteadOfDisappearing() {
        registered(true);
        manager(false);
        existing(new ContestBalloon().setId(9L).setSubmissionId(55L).setStatus("PENDING"));
        when(balloons.selectFirstValidAccepted(CONTEST, USER, PROBLEM)).thenReturn(60L);

        service.reconcileSubmission(55L);

        verify(balloons).update(isNull(), any());
        verify(balloons, never()).insert(any(ContestBalloon.class));
    }

    @Test void nonContestSubmissionsAreIgnored() {
        when(contestSubmissions.findContestIdBySubmissionId(55L)).thenReturn(null);

        service.reconcileSubmission(55L);

        verifyNoInteractions(balloons);
    }

    @Test void reconcileWaitsForTheJudgeTransactionToCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.reconcileAfterCommit(List.of(55L));
            // 主事务还没提交，不能碰气球表
            verifyNoInteractions(balloons, contestSubmissions);
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void reconcileFailuresNeverEscape() {
        when(contestSubmissions.findContestIdBySubmissionId(55L)).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> service.reconcileAfterCommit(List.of(55L)));
    }
}

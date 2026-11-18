package com.seuoj.seuojbackend.unit.contest;

import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.service.contest.AcmRankingStrategy;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcmRankingStrategyTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 18, 13, 0);

    private static Submission sub(long id, long problemId, int minute, String verdict) {
        return new Submission().setId(id).setUserId(1L).setProblemId(problemId)
                .setSubmitTime(START.plusMinutes(minute)).setVerdict(verdict);
    }

    private static ContestStandingsRecordVO standings(List<Submission> subs) {
        Contest contest = new Contest().setStartTime(START);
        Map<Long, UserInfo> users = Map.of(1L, new UserInfo().setId(1L).setUsername("u1"));
        Map<Long, String> pids = Map.of(10L, "A", 20L, "B");
        return new AcmRankingStrategy().computeStandings(List.of(), users, subs, contest, pids).get(0);
    }

    @Test void onlyJudgedWrongVerdictsArePenalized() {
        var r = standings(List.of(
                sub(1, 10, 1, "CompileError"),
                sub(2, 10, 2, "WrongAnswer"),
                sub(3, 10, 3, "CodeTooLong"),
                sub(4, 10, 4, "JudgendError"),
                sub(5, 10, 5, "SystemError"),
                sub(6, 10, 6, "Skipped"),
                sub(7, 10, 7, "TimeLimitExceeded"),
                sub(8, 10, 30, "Accepted")));
        assertEquals(1, r.getScore());
        assertEquals(30 + 2 * 20, r.getPenalty());
        assertEquals(2, r.getScoreDetails().get("A").getUnacceptedCount());
    }

    @Test void problemWithOnlyCompileErrorsShowsAsTriedWithoutPenalty() {
        var r = standings(List.of(sub(1, 20, 5, "CompileError")));
        var b = r.getScoreDetails().get("B");
        assertFalse(b.getAccepted());
        assertEquals(0, b.getUnacceptedCount());
        assertEquals(0, r.getPenalty());
    }
}

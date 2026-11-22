package com.seuoj.seuojbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * fail-fast 之后结果里会出现未运行的测试点，判定不能受它们影响。
 */
class JudgeVerdictTest {

    private static JudgeResultDetailItem caseOf(int id, SubmissionVerdict type) {
        JudgeResultDetailItem item = new JudgeResultDetailItem();
        item.setId(id);
        item.setType(type);
        item.setIn("");
        item.setOut("");
        item.setAns("");
        item.setSys("");
        item.setTime(0L);
        item.setMem(0L);
        item.setScore(SubmissionVerdict.ACCEPTED.equals(type) ? 100 : 0);
        return item;
    }

    @Test
    void allAcceptedIsAccepted() {
        assertEquals(SubmissionVerdict.ACCEPTED.getVerdict(), JudgeService.deriveVerdict(List.of(
                caseOf(1, SubmissionVerdict.ACCEPTED),
                caseOf(2, SubmissionVerdict.ACCEPTED))));
    }

    @Test
    void firstFailureWinsAndSkippedTailIsIgnored() {
        assertEquals(SubmissionVerdict.WRONG_ANSWER.getVerdict(), JudgeService.deriveVerdict(List.of(
                caseOf(1, SubmissionVerdict.ACCEPTED),
                caseOf(2, SubmissionVerdict.WRONG_ANSWER),
                caseOf(3, SubmissionVerdict.SKIPPED),
                caseOf(4, SubmissionVerdict.SKIPPED))));
    }

    @Test
    void skippedBeforeFailureDoesNotOverrideIt() {
        assertEquals(SubmissionVerdict.TIME_LIMIT_EXCEEDED.getVerdict(), JudgeService.deriveVerdict(List.of(
                caseOf(1, SubmissionVerdict.SKIPPED),
                caseOf(2, SubmissionVerdict.TIME_LIMIT_EXCEEDED))));
    }

    @Test
    void skippedWithoutFailureIsAnError() {
        assertThrows(BadRequestException.class, () -> JudgeService.deriveVerdict(List.of(
                caseOf(1, SubmissionVerdict.ACCEPTED),
                caseOf(2, SubmissionVerdict.SKIPPED))));
    }
}

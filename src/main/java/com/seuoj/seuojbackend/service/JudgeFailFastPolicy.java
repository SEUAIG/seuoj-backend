package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestSubmissionMapper;
import org.springframework.stereotype.Component;

/**
 * 决定一次评测是否启用 fail-fast（首个失败测试点后跳过剩余测试点）。
 * <p>
 * 只有 ACM 赛制是二值评分（AC 计 1 题、否则 0 题），跳过剩余测试点不会改变判定结果；
 * IOI/OI/自定义脚本赛制依赖完整逐点结果或部分分，不能启用。
 * 题目是否带子任务由判题端自行判断，带子任务时判题端会忽略该标志。
 */
@Component
public class JudgeFailFastPolicy {

    private static final String ACM_RULE = "ACM";

    private final ContestSubmissionMapper contestSubmissions;
    private final ContestMapper contests;

    public JudgeFailFastPolicy(ContestSubmissionMapper contestSubmissions, ContestMapper contests) {
        this.contestSubmissions = contestSubmissions;
        this.contests = contests;
    }

    /** 已知比赛对象时直接判断，避免多查一次库。 */
    public boolean enabledForContest(Contest contest) {
        return contest != null && ACM_RULE.equalsIgnoreCase(contest.getRuleType());
    }

    /** 已知提交时反查比赛；不属于任何比赛的普通提交不启用。 */
    public boolean enabledForSubmission(Long submissionId) {
        if (submissionId == null) {
            return false;
        }
        Long contestId = contestSubmissions.findContestIdBySubmissionId(submissionId);
        return contestId != null && enabledForContest(contests.selectById(contestId));
    }
}

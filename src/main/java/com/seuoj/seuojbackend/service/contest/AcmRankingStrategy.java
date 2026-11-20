package com.seuoj.seuojbackend.service.contest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsScoreDetailVO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AcmRankingStrategy implements ContestRankingStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 只有程序跑过测试数据后的错误才计罚时；CE、代码过长、判题端/系统错误、跳过都不算。 */
    private static final Set<String> PENALIZED_VERDICTS = Set.of(
            SubmissionVerdict.WRONG_ANSWER.getVerdict(),
            SubmissionVerdict.TIME_LIMIT_EXCEEDED.getVerdict(),
            SubmissionVerdict.MEMORY_LIMIT_EXCEEDED.getVerdict(),
            SubmissionVerdict.RUNTIME_ERROR.getVerdict(),
            SubmissionVerdict.PARTIALLY_ACCEPTED.getVerdict());

    @Override
    public List<ContestStandingsRecordVO> computeStandings(
            List<ContestProblemOverviewVO> problems,
            Map<Long, UserInfo> registeredUsers,
            List<Submission> finishedSubmissions,
            Contest contest,
            Map<Long, String> problemIdToPid) {

        int penaltyMinutes = parsePenaltyMinutes(contest.getScoringConfig());

        Map<Long, Map<Long, ProblemTracker>> userMap = new HashMap<>();
        for (Long userId : registeredUsers.keySet()) {
            userMap.put(userId, new HashMap<>());
        }

        for (Submission s : finishedSubmissions) {
            Map<Long, ProblemTracker> problemMap = userMap.get(s.getUserId());
            if (problemMap == null) continue;
            if (!problemIdToPid.containsKey(s.getProblemId())) continue;

            ProblemTracker tracker = problemMap.computeIfAbsent(s.getProblemId(), k -> new ProblemTracker());

            if (SubmissionVerdict.ACCEPTED.getVerdict().equals(s.getVerdict())) {
                if (!tracker.accepted) {
                    tracker.accepted = true;
                    tracker.acceptedTimeMinutes = Duration.between(contest.getStartTime(), s.getSubmitTime()).toMinutes();
                    // 一血/详情按“首次通过”的提交算
                    tracker.firstSubmissionId = s.getId();
                }
                // 提交按 (submit_time, id) 升序遍历，最后一次 AC 的 id 即最终值
                tracker.lastAcceptedSubmissionId = s.getId();
            } else if (!tracker.accepted && PENALIZED_VERDICTS.contains(s.getVerdict())) {
                // 通过之后的错误提交不再计罚时
                tracker.failedAttempts++;
            }
        }

        List<UserStanding> standings = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, ProblemTracker>> entry : userMap.entrySet()) {
            Long userId = entry.getKey();
            Map<Long, ProblemTracker> problemMap = entry.getValue();
            UserInfo user = registeredUsers.get(userId);

            int solvedCount = 0;
            long totalPenalty = 0;
            for (ProblemTracker tracker : problemMap.values()) {
                if (tracker.accepted) {
                    solvedCount++;
                    totalPenalty += tracker.acceptedTimeMinutes + (long) tracker.failedAttempts * penaltyMinutes;
                }
            }

            UserStanding standing = new UserStanding();
            standing.username = user != null ? user.getUsername() : null;
            standing.nickname = user != null ? user.getNickname() : null;
            standing.solvedCount = solvedCount;
            standing.penalty = totalPenalty;
            standing.problemTrackers = problemMap;
            // 并列时的 tie-break：最后一个正确解答的提交编号，越小越靠前
            standing.lastSolveSubmissionId = problemMap.values().stream()
                    .filter(t -> t.accepted && t.lastAcceptedSubmissionId != null)
                    .map(t -> t.lastAcceptedSubmissionId)
                    .max(Long::compareTo)
                    .orElse(null);
            standings.add(standing);
        }

        standings.sort(Comparator
                .comparingInt((UserStanding s) -> s.solvedCount).reversed()
                .thenComparingLong(s -> s.penalty)
                // ACM tie-break：最后一道通过题的提交编号小者在前
                .thenComparing((UserStanding s) -> s.lastSolveSubmissionId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                // 兜底保证顺序稳定，避免并列时每次刷新顺序抖动
                .thenComparing(s -> s.username == null ? "" : s.username));

        List<ContestStandingsRecordVO> result = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < standings.size(); i++) {
            UserStanding current = standings.get(i);
            if (i > 0) {
                UserStanding prev = standings.get(i - 1);
                boolean tied = current.solvedCount == prev.solvedCount
                        && current.penalty == prev.penalty
                        && java.util.Objects.equals(current.lastSolveSubmissionId, prev.lastSolveSubmissionId);
                if (!tied) {
                    rank = i + 1;
                }
            }

            ContestStandingsRecordVO record = new ContestStandingsRecordVO();
            record.setRank(rank);
            record.setUsername(current.username);
            record.setNickname(current.nickname);
            record.setScore(current.solvedCount);
            record.setPenalty(current.penalty);

            Map<String, ContestStandingsScoreDetailVO> scoreDetails = new LinkedHashMap<>();
            for (Map.Entry<Long, ProblemTracker> pe : current.problemTrackers.entrySet()) {
                String pid = problemIdToPid.get(pe.getKey());
                if (pid == null) continue;
                ProblemTracker tracker = pe.getValue();

                ContestStandingsScoreDetailVO detail = new ContestStandingsScoreDetailVO();
                detail.setAccepted(tracker.accepted);
                detail.setUnacceptedCount(tracker.failedAttempts);
                detail.setAcceptedTime(tracker.accepted ? tracker.acceptedTimeMinutes : null);
                detail.setPenaltyTime(tracker.accepted
                        ? tracker.acceptedTimeMinutes + (long) tracker.failedAttempts * penaltyMinutes
                        : null);
                detail.setScore(tracker.accepted ? 1 : 0);
                // 一血判定用首次通过的提交
                detail.setJudgeId(tracker.firstSubmissionId);
                scoreDetails.put(pid, detail);
            }
            record.setScoreDetails(scoreDetails);
            result.add(record);
        }
        return result;
    }

    private int parsePenaltyMinutes(String scoringConfig) {
        if (scoringConfig == null || scoringConfig.isBlank()) return 20;
        try {
            Map<String, Object> config = OBJECT_MAPPER.readValue(scoringConfig,
                    new TypeReference<Map<String, Object>>() {});
            Object val = config.get("penalty_minutes");
            if (val instanceof Number n) return n.intValue();
        } catch (Exception e) {
            log.warn("Failed to parse scoring_config: {}", scoringConfig, e);
        }
        return 20;
    }

    private static class ProblemTracker {
        boolean accepted = false;
        int failedAttempts = 0;
        long acceptedTimeMinutes = 0;
        /** 本题首次通过的提交 id（一血用） */
        Long firstSubmissionId = null;
        /** 本题最后一次通过的提交 id（并列排名用） */
        Long lastAcceptedSubmissionId = null;
    }

    private static class UserStanding {
        String username;
        String nickname;
        int solvedCount;
        long penalty;
        /** 最后一个正确解答的提交 id */
        Long lastSolveSubmissionId;
        Map<Long, ProblemTracker> problemTrackers;
    }
}

package com.seuoj.seuojbackend.service.contest;

import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsScoreDetailVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IoiRankingStrategy implements ContestRankingStrategy {

    @Override
    public List<ContestStandingsRecordVO> computeStandings(
            List<ContestProblemOverviewVO> problems,
            Map<Long, UserInfo> registeredUsers,
            List<Submission> finishedSubmissions,
            Contest contest,
            Map<Long, String> problemIdToPid) {

        // For each (user, problem): take the MAX score across all submissions
        Map<Long, Map<Long, ProblemScore>> userMap = new HashMap<>();
        for (Long userId : registeredUsers.keySet()) {
            userMap.put(userId, new HashMap<>());
        }

        for (Submission s : finishedSubmissions) {
            Map<Long, ProblemScore> problemMap = userMap.get(s.getUserId());
            if (problemMap == null) continue;
            if (!problemIdToPid.containsKey(s.getProblemId())) continue;

            int submissionScore = s.getScore() != null ? s.getScore() : 0;
            ProblemScore existing = problemMap.get(s.getProblemId());
            if (existing == null || submissionScore > existing.score) {
                ProblemScore ps = new ProblemScore();
                ps.score = submissionScore;
                ps.submissionId = s.getId();
                problemMap.put(s.getProblemId(), ps);
            }
        }

        List<UserStanding> standings = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, ProblemScore>> entry : userMap.entrySet()) {
            Long userId = entry.getKey();
            Map<Long, ProblemScore> problemMap = entry.getValue();
            UserInfo user = registeredUsers.get(userId);

            int totalScore = 0;
            for (ProblemScore ps : problemMap.values()) {
                totalScore += ps.score;
            }

            UserStanding standing = new UserStanding();
            standing.username = user != null ? user.getUsername() : null;
            standing.nickname = user != null ? user.getNickname() : null;
            standing.totalScore = totalScore;
            standing.problemScores = problemMap;
            standings.add(standing);
        }

        standings.sort(Comparator.comparingInt((UserStanding s) -> s.totalScore).reversed());

        List<ContestStandingsRecordVO> result = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < standings.size(); i++) {
            UserStanding current = standings.get(i);
            if (i > 0 && current.totalScore != standings.get(i - 1).totalScore) {
                rank = i + 1;
            }

            ContestStandingsRecordVO record = new ContestStandingsRecordVO();
            record.setRank(rank);
            record.setUsername(current.username);
            record.setNickname(current.nickname);
            record.setScore(current.totalScore);

            Map<String, ContestStandingsScoreDetailVO> scoreDetails = new LinkedHashMap<>();
            for (Map.Entry<Long, ProblemScore> pe : current.problemScores.entrySet()) {
                String pid = problemIdToPid.get(pe.getKey());
                if (pid == null) continue;
                ProblemScore ps = pe.getValue();

                ContestStandingsScoreDetailVO detail = new ContestStandingsScoreDetailVO();
                detail.setScore(ps.score);
                detail.setJudgeId(ps.submissionId);
                detail.setAccepted(ps.score >= 100);
                scoreDetails.put(pid, detail);
            }
            record.setScoreDetails(scoreDetails);
            result.add(record);
        }
        return result;
    }

    private static class ProblemScore {
        int score = 0;
        Long submissionId = null;
    }

    private static class UserStanding {
        String username;
        String nickname;
        int totalScore;
        Map<Long, ProblemScore> problemScores;
    }
}

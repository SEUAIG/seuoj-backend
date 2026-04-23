package com.seuoj.seuojbackend.service.contest;

import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import java.util.List;
import java.util.Map;

public interface ContestRankingStrategy {

    List<ContestStandingsRecordVO> computeStandings(
            List<ContestProblemOverviewVO> problems,
            Map<Long, UserInfo> registeredUsers,
            List<Submission> finishedSubmissions,
            Contest contest,
            Map<Long, String> problemIdToPid
    );
}

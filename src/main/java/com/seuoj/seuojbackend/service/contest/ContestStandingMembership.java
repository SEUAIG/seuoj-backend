package com.seuoj.seuojbackend.service.contest;

import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import java.util.*;

/** Keep each ranking strategy's order and ties, excluding starred entrants from numbered ranks. */
public final class ContestStandingMembership {
    private ContestStandingMembership() {}

    public static void apply(List<ContestStandingsRecordVO> records, List<ContestRegisterRel> members,
                             Map<Long, UserInfo> users) {
        Map<String, ContestRegisterRel> byUsername = new HashMap<>();
        for (var member : members) {
            var user = users.get(member.getUserId());
            if (user != null) byUsername.put(user.getUsername(), member);
        }
        int officialCount = 0;
        int officialRank = 0;
        Integer previousOriginalRank = null;
        for (var record : records) {
            var member = byUsername.get(record.getUsername());
            record.setDisplayName(member == null ? null : member.getDisplayName());
            boolean starred = member != null && Boolean.TRUE.equals(member.getIsStarred());
            record.setIsStarred(starred);
            Integer originalRank = record.getRank();
            if (starred) {
                record.setRank(null);
            } else {
                officialCount++;
                if (!Objects.equals(previousOriginalRank, originalRank) || officialRank == 0) {
                    officialRank = officialCount;
                }
                record.setRank(officialRank);
                previousOriginalRank = originalRank;
            }
        }
    }
}

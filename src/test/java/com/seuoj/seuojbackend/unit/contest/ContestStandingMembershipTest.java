package com.seuoj.seuojbackend.unit.contest;

import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.service.contest.ContestStandingMembership;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContestStandingMembershipTest {
    @Test void excludesStarsAndPreservesTiesAndCompetitionRankGaps() {
        var users = new LinkedHashMap<Long, UserInfo>();
        var members = new ArrayList<ContestRegisterRel>();
        var records = new ArrayList<ContestStandingsRecordVO>();
        int[] originalRanks = {1, 2, 2, 2, 5, 6};
        boolean[] starred = {true, false, true, false, true, false};
        for (int i = 0; i < originalRanks.length; i++) {
            long id = i + 1;
            users.put(id, new UserInfo().setId(id).setUsername("user" + id).setNickname("nickname"));
            members.add(new ContestRegisterRel().setUserId(id).setDisplayName("姓名" + id).setIsStarred(starred[i]));
            var record = new ContestStandingsRecordVO();
            record.setUsername("user" + id);
            record.setRank(originalRanks[i]);
            record.setScore(5);
            records.add(record);
        }
        ContestStandingMembership.apply(records, members, users);
        assertEquals(Arrays.asList(null, 1, null, 1, null, 3), records.stream().map(ContestStandingsRecordVO::getRank).toList());
        assertEquals("姓名2", records.get(1).getDisplayName());
        assertEquals("nickname", users.get(2L).getNickname());
        assertTrue(records.get(0).getIsStarred());
        assertEquals(5, records.get(0).getScore());
    }

    @Test void existingRegistrationsWithoutMetadataRetainRanks() {
        var a = new ContestStandingsRecordVO(); a.setUsername("a"); a.setRank(1);
        var b = new ContestStandingsRecordVO(); b.setUsername("b"); b.setRank(1);
        var c = new ContestStandingsRecordVO(); c.setUsername("c"); c.setRank(3);
        var records = List.of(a, b, c);
        ContestStandingMembership.apply(records, List.of(), Map.of());
        assertEquals(List.of(1, 1, 3), records.stream().map(ContestStandingsRecordVO::getRank).toList());
        assertFalse(a.getIsStarred());
        assertNull(a.getDisplayName());
    }
}

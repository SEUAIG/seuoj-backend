package com.seuoj.seuojbackend.mapper;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProblemAccessMapper {

    boolean existsContestProblemRelation(@Param("contestPublicId") String contestPublicId,
                                         @Param("problemId") Long problemId);

    ContestAccessRow selectContestAccess(@Param("contestPublicId") String contestPublicId,
                                         @Param("userId") Long userId);

    boolean existsProblemSetRelation(@Param("problemSetPublicId") String problemSetPublicId,
                                     @Param("problemId") Long problemId);

    ProblemSetAccessRow selectProblemSetAccess(@Param("problemSetPublicId") String problemSetPublicId,
                                               @Param("userId") Long userId);

    @Getter
    @Setter
    class ContestAccessRow {
        private Long contestId;
        private Boolean isPublic;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean manager;
        private boolean registered;
        private boolean classMember;
    }

    @Getter
    @Setter
    class ProblemSetAccessRow {
        private Long problemSetId;
        private Boolean isPublic;
        private boolean owner;
        private boolean classMember;
        private boolean sharedUser;
    }
}

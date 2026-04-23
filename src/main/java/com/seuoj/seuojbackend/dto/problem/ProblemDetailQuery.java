package com.seuoj.seuojbackend.dto.problem;

import com.seuoj.seuojbackend.common.ProblemSourceType;
import com.seuoj.seuojbackend.exception.BadRequestException;

public record ProblemDetailQuery(String pid, ProblemSourceType sourceType, Long ownerId) {

    public ProblemDetailQuery {
        if (pid == null || pid.isBlank()) {
            throw new BadRequestException("pid 不能为空");
        }
        if (sourceType == null) {
            throw new BadRequestException("source_type 不能为空");
        }
        if (sourceType == ProblemSourceType.DIRECT && ownerId != null) {
            throw new BadRequestException("公开题模式下不能传上下文 id");
        }
        if (sourceType != ProblemSourceType.DIRECT && ownerId == null) {
            throw new BadRequestException("上下文 id 不能为空");
        }
    }

    public static ProblemDetailQuery fromRequest(String pid, Long contestId, Long problemSetId) {
        boolean hasContest = contestId != null;
        boolean hasProblemSet = problemSetId != null;

        if (hasContest && hasProblemSet) {
            throw new BadRequestException("contest_id 和 problem_set_id 不能同时传");
        }
        if (!hasContest && !hasProblemSet) {
            return direct(pid);
        }
        if (hasContest) {
            return new ProblemDetailQuery(pid, ProblemSourceType.CONTEST, contestId);
        }
        return new ProblemDetailQuery(pid, ProblemSourceType.PROBLEM_SET, problemSetId);
    }

    public static ProblemDetailQuery direct(String pid) {
        return new ProblemDetailQuery(pid, ProblemSourceType.DIRECT, null);
    }
}

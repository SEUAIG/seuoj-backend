package com.seuoj.seuojbackend.dto.problem;

import com.seuoj.seuojbackend.common.ProblemSourceType;
import com.seuoj.seuojbackend.exception.BadRequestException;

public record ProblemDetailQuery(String pid, ProblemSourceType sourceType, String ownerPublicId) {

    public ProblemDetailQuery {
        if (pid == null || pid.isBlank()) {
            throw new BadRequestException("pid 不能为空");
        }
        if (sourceType == null) {
            throw new BadRequestException("source_type 不能为空");
        }
        if (sourceType == ProblemSourceType.DIRECT && ownerPublicId != null) {
            throw new BadRequestException("公开题模式下不能传上下文 id");
        }
        if (sourceType != ProblemSourceType.DIRECT && (ownerPublicId == null || ownerPublicId.isBlank())) {
            throw new BadRequestException("上下文 public_id 不能为空");
        }
    }

    public static ProblemDetailQuery fromRequest(String pid, String contestPublicId, String problemSetPublicId) {
        boolean hasContest = contestPublicId != null && !contestPublicId.isBlank();
        boolean hasProblemSet = problemSetPublicId != null && !problemSetPublicId.isBlank();

        if (hasContest && hasProblemSet) {
            throw new BadRequestException("contest_public_id 和 problem_set_public_id 不能同时传");
        }
        if (!hasContest && !hasProblemSet) {
            return direct(pid);
        }
        if (hasContest) {
            return new ProblemDetailQuery(pid, ProblemSourceType.CONTEST, contestPublicId.trim());
        }
        return new ProblemDetailQuery(pid, ProblemSourceType.PROBLEM_SET, problemSetPublicId.trim());
    }

    public static ProblemDetailQuery direct(String pid) {
        return new ProblemDetailQuery(pid, ProblemSourceType.DIRECT, null);
    }
}

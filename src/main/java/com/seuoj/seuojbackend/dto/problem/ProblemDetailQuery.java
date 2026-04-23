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

    public static ProblemDetailQuery fromRequest(String pid, Long contestId, Long problemSetId, Long assignmentId) {
        int contextCount = (contestId != null ? 1 : 0) + (problemSetId != null ? 1 : 0) + (assignmentId != null ? 1 : 0);
        if (contextCount > 1) {
            throw new BadRequestException("contest_id、problem_set_id、assignment_id 不能同时传多个");
        }
        if (contextCount == 0) {
            return direct(pid);
        }
        if (contestId != null) {
            return new ProblemDetailQuery(pid, ProblemSourceType.CONTEST, contestId);
        }
        if (assignmentId != null) {
            return new ProblemDetailQuery(pid, ProblemSourceType.ASSIGNMENT, assignmentId);
        }
        return new ProblemDetailQuery(pid, ProblemSourceType.PROBLEM_SET, problemSetId);
    }

    public static ProblemDetailQuery direct(String pid) {
        return new ProblemDetailQuery(pid, ProblemSourceType.DIRECT, null);
    }
}

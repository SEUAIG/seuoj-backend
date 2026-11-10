package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Shared by the contest and generic submission endpoints. Never mutates stored judge results. */
@Service
public class ContestSubmissionPolicy {
    private final PermissionService permissions;
    private final UserRoleService roles;

    public ContestSubmissionPolicy(PermissionService permissions, UserRoleService roles) {
        this.permissions = permissions;
        this.roles = roles;
    }

    public void assertCanView(Long viewerId, Contest contest, Submission submission) {
        permissions.assertPermission(viewerId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);
        if (!Objects.equals(viewerId, submission.getUserId()) && !roles.isAdmin(viewerId)
                && !permissions.hasPermission(viewerId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE)) {
            throw new ForbiddenException("无权查看他人提交记录");
        }
    }

    public record Result(List<JudgeResultDetailItem> cases, List<ProblemConfigDTO.Subtask> subtasks,
                         String error, Integer score, Long maxTime, Long maxMemory, boolean testDataVisible) {}

    public Result project(Long viewerId, Contest contest, Submission submission, SubmissionDetail detail) {
        boolean manager = roles.isAdmin(viewerId)
                || permissions.hasPermission(viewerId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE);
        boolean ioi = "IOI".equals(contest.getRuleType());
        boolean finished = "Finished".equals(submission.getStatus()) || "Failed".equals(submission.getStatus());
        // Intermediate per-case updates must not become a progress/count side channel.
        if (!manager && !finished) return new Result(null, null, null, null, null, null, false);

        List<JudgeResultDetailItem> raw = detail == null ? null : detail.getResultDetail();
        Long maxTime = raw == null ? null : raw.stream().filter(Objects::nonNull)
                .map(JudgeResultDetailItem::getTime).filter(Objects::nonNull).max(Long::compare).orElse(null);
        Long maxMemory = raw == null ? null : raw.stream().filter(Objects::nonNull)
                .map(JudgeResultDetailItem::getMem).filter(Objects::nonNull).max(Long::compare).orElse(null);
        List<JudgeResultDetailItem> cases = null;
        if (manager) {
            cases = raw;
        } else if (ioi && raw != null) {
            cases = raw.stream().filter(Objects::nonNull).map(item -> {
                JudgeResultDetailItem safe = new JudgeResultDetailItem();
                safe.setId(item.getId());
                safe.setTime(item.getTime());
                safe.setMem(item.getMem());
                safe.setType(item.getType());
                safe.setScore(item.getScore());
                return safe;
            }).toList();
        }
        String error = detail == null ? null : detail.getErrorDetail();
        if (!manager) {
            error = "CompileError".equals(submission.getVerdict()) ? truncateCompileError(error) : null;
        }
        return new Result(cases, (manager || ioi) && detail != null ? detail.getSubtasks() : null,
                error, manager || ioi ? submission.getScore() : null, maxTime, maxMemory, manager);
    }

    private String truncateCompileError(String text) {
        if (text == null || text.length() <= 2000) return text;
        String suffix = "...（内容已截断）";
        return text.substring(0, 2000 - suffix.length()) + suffix;
    }
}

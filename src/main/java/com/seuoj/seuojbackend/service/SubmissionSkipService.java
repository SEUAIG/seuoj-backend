package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.ContestSubmissionMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.entity.UserInfo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提交 skip：被跳过的提交保留原结果，但不计入榜单、题目通过数和各类统计。
 * <p>
 * 权限：比赛提交要求该比赛 WRITE；其余提交要求题目 WRITE，作业提交也可由班级 WRITE 处理。
 * 一次请求整体生效或整体不生效。
 */
@Slf4j @Service @RequiredArgsConstructor
public class SubmissionSkipService {
    public static final int MAX_BATCH = 500;
    private final SubmissionMapper submissions;
    private final ContestSubmissionMapper contestSubmissions;
    private final ProblemMapper problems;
    private final AssignmentMapper assignments;
    private final UserInfoMapper users;
    private final PermissionService permissions;

    public record SkipRequest(@Size(max = MAX_BATCH, message = "一次最多处理 500 条提交") List<String> submissionNos,
                              @NotNull(message = "skipped 不能为空") Boolean skipped,
                              @Size(max = 200, message = "原因不能超过200个字符") String reason) {}
    /** 比赛内按选手整体跳过：username 与 submissionNos 二选一或同时给出 */
    public record ContestSkipRequest(@Size(max = MAX_BATCH, message = "一次最多处理 500 条提交") List<String> submissionNos,
                                     @Size(max = 64) String username,
                                     @NotNull(message = "skipped 不能为空") Boolean skipped,
                                     @Size(max = 200, message = "原因不能超过200个字符") String reason) {}
    public record Outcome(int matched, int changed) {}

    @Transactional
    public Outcome skip(Long actor, SkipRequest dto) {
        Set<String> nos = normalize(dto.submissionNos());
        if (nos.isEmpty()) throw new BadRequestException("请至少选择一条提交");
        List<Submission> targets = lockByNos(nos);
        assertCanManage(actor, targets);
        return apply(actor, targets, dto.skipped(), dto.reason());
    }

    @Transactional
    public Outcome skipInContest(Long actor, Long contestId, ContestSkipRequest dto) {
        permissions.assertPermission(actor, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
        Set<String> nos = normalize(dto.submissionNos());
        String username = dto.username() == null ? null : dto.username().trim();
        if (nos.isEmpty() && (username == null || username.isEmpty())) throw new BadRequestException("请至少选择一条提交或一个选手");

        String inContest = "SELECT submission_id FROM contest_submission WHERE is_del = 0 AND contest_id = " + contestId;
        Map<Long, Submission> found = new LinkedHashMap<>();
        if (!nos.isEmpty()) {
            List<Submission> listed = lockByNos(nos);
            Set<Long> inThisContest = new HashSet<>();
            for (Submission s : submissions.selectList(new LambdaQueryWrapper<Submission>().select(Submission::getId)
                    .in(Submission::getId, listed.stream().map(Submission::getId).toList()).inSql(Submission::getId, inContest)))
                inThisContest.add(s.getId());
            List<String> outside = listed.stream().filter(s -> !inThisContest.contains(s.getId())).map(Submission::getSubmissionNo).toList();
            if (!outside.isEmpty()) throw new BadRequestException("这些提交不属于本比赛：" + String.join("、", outside));
            listed.forEach(s -> found.put(s.getId(), s));
        }
        if (username != null && !username.isEmpty()) {
            List<UserInfo> matched = users.selectList(new LambdaQueryWrapper<UserInfo>().select(UserInfo::getId).eq(UserInfo::getUsername, username));
            if (matched.isEmpty()) throw new BadRequestException("找不到账号：" + username);
            List<Long> userIds = matched.stream().map(UserInfo::getId).toList();
            for (Submission s : submissions.selectList(new LambdaQueryWrapper<Submission>()
                    .in(Submission::getUserId, userIds).inSql(Submission::getId, inContest).last("FOR UPDATE")))
                found.putIfAbsent(s.getId(), s);
        }
        return apply(actor, new ArrayList<>(found.values()), dto.skipped(), dto.reason());
    }

    /** 当前用户能否 skip 这条提交（详情页决定是否显示按钮） */
    public boolean canSkip(Long actor, Submission submission) {
        if (actor == null || submission == null) return false;
        try {
            assertCanManage(actor, List.of(submission));
            return true;
        } catch (ForbiddenException e) {
            return false;
        }
    }

    public boolean canSkip(Long actor, String submissionNo) {
        if (actor == null || submissionNo == null) return false;
        return canSkip(actor, submissions.selectOne(new LambdaQueryWrapper<Submission>().eq(Submission::getSubmissionNo, submissionNo)));
    }

    private static Set<String> normalize(List<String> raw) {
        Set<String> nos = new LinkedHashSet<>();
        if (raw != null) for (String no : raw) if (no != null && !no.isBlank()) nos.add(no.trim());
        if (nos.size() > MAX_BATCH) throw new BadRequestException("一次最多处理 " + MAX_BATCH + " 条提交");
        return nos;
    }

    private List<Submission> lockByNos(Set<String> nos) {
        List<Submission> rows = submissions.selectList(new LambdaQueryWrapper<Submission>()
                .in(Submission::getSubmissionNo, nos).last("FOR UPDATE"));
        Set<String> seen = new HashSet<>();
        rows.forEach(s -> seen.add(s.getSubmissionNo()));
        List<String> missing = nos.stream().filter(no -> !seen.contains(no)).toList();
        if (!missing.isEmpty()) throw new BadRequestException("找不到这些提交：" + String.join("、", missing));
        return rows;
    }

    /** 管理提交（skip、重测）的权限：比赛提交认比赛 WRITE，其余认题目 WRITE 或作业所在班级 WRITE。 */
    public void assertCanManage(Long actor, List<Submission> targets) {
        Map<Long, Boolean> contestCache = new HashMap<>(), problemCache = new HashMap<>(), classCache = new HashMap<>();
        List<String> denied = new ArrayList<>();
        for (Submission s : targets) {
            Long contestId = contestSubmissions.findContestIdBySubmissionId(s.getId());
            boolean ok;
            if (contestId != null) {
                ok = contestCache.computeIfAbsent(contestId, id -> permissions.hasPermission(actor, ResourceType.CONTEST, id, PermissionOp.WRITE));
            } else {
                ok = problemCache.computeIfAbsent(s.getProblemId(), id -> permissions.hasPermission(actor, ResourceType.PROBLEM, id, PermissionOp.WRITE));
                if (!ok && s.getAssignmentId() != null) {
                    Assignment assignment = assignments.selectById(s.getAssignmentId());
                    if (assignment != null && assignment.getClassId() != null)
                        ok = classCache.computeIfAbsent(assignment.getClassId(), id -> permissions.hasPermission(actor, ResourceType.CLASS, id, PermissionOp.WRITE));
                }
            }
            if (!ok) denied.add(s.getSubmissionNo());
        }
        if (!denied.isEmpty()) {
            List<String> shown = denied.size() > 5 ? denied.subList(0, 5) : denied;
            throw new ForbiddenException("无权处理这些提交：" + String.join("、", shown) + (denied.size() > 5 ? " 等 " + denied.size() + " 条" : ""));
        }
    }

    private Outcome apply(Long actor, List<Submission> targets, boolean skipped, String rawReason) {
        String reason = rawReason == null || rawReason.isBlank() ? null : rawReason.trim();
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        // 题目计数按题目汇总后一次修正：跳过 = 撤销这次提交（以及通过）对计数的贡献
        Map<Long, int[]> deltas = new HashMap<>();
        for (Submission s : targets) {
            if (Boolean.TRUE.equals(s.getIsSkipped()) == skipped) continue;
            LambdaUpdateWrapper<Submission> update = new LambdaUpdateWrapper<Submission>().eq(Submission::getId, s.getId())
                    .set(Submission::getIsSkipped, skipped);
            if (skipped) update.set(Submission::getSkippedAt, now).set(Submission::getSkippedBy, actor).set(Submission::getSkipReason, reason);
            else update.set(Submission::getSkippedAt, null).set(Submission::getSkippedBy, null).set(Submission::getSkipReason, null);
            submissions.update(null, update);
            int sign = skipped ? -1 : 1;
            boolean accepted = SubmissionStatus.FINISHED.getStatus().equals(s.getStatus())
                    && SubmissionVerdict.ACCEPTED.getVerdict().equals(s.getVerdict());
            int[] d = deltas.computeIfAbsent(s.getProblemId(), k -> new int[2]);
            d[0] += sign;
            if (accepted) d[1] += sign;
            changed++;
        }
        deltas.forEach((problemId, d) -> problems.adjustTotalCounts(problemId, d[0], d[1]));
        if (changed > 0) log.info("用户 {} {}了 {} 条提交", actor, skipped ? "跳过" : "取消跳过", changed);
        return new Outcome(targets.size(), changed);
    }
}

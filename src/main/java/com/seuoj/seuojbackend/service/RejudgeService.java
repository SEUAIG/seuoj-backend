package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionDetailMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 重测：把提交重置为 Pending、撤销原通过对题目通过数的贡献，事务提交后交给 {@link RejudgeDispatcher} 逐条送评。
 * 正在评测的提交不重测；被 skip 的提交保持 skip。权限与 skip 相同。
 */
@Slf4j @Service @RequiredArgsConstructor
public class RejudgeService {
    public static final int MAX_SELECTED = 500;
    /** 整题重测的上限，防止误操作一次塞进过多提交 */
    public static final int MAX_WHOLE_PROBLEM = 5000;

    private final SubmissionMapper submissions;
    private final SubmissionDetailMapper details;
    private final ProblemMapper problems;
    private final PermissionService permissions;
    private final SubmissionSkipService skips;
    private final RejudgeDispatcher dispatcher;

    public record SelectedRequest(@Size(max = MAX_SELECTED, message = "一次最多选 500 条提交") List<String> submissionNos) {}
    /** 比赛内：按提交编号，或给 pid 重测本场该题全部提交 */
    public record ContestRequest(@Size(max = MAX_SELECTED, message = "一次最多选 500 条提交") List<String> submissionNos,
                                 @Size(max = 64) String pid) {}
    /** queued：已重置并排队；busy：正在评测而跳过的条数 */
    public record Outcome(int queued, int busy, int queueLength) {}

    @Transactional
    public Outcome rejudgeSelected(Long actor, SelectedRequest dto) {
        Set<String> nos = normalize(dto.submissionNos());
        if (nos.isEmpty()) throw new BadRequestException("请至少选择一条提交");
        List<Submission> targets = lockByNos(nos);
        skips.assertCanManage(actor, targets);
        return reset(actor, targets);
    }

    @Transactional
    public Outcome rejudgeInContest(Long actor, Long contestId, ContestRequest dto) {
        permissions.assertPermission(actor, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
        Set<String> nos = normalize(dto.submissionNos());
        String pid = dto.pid() == null || dto.pid().isBlank() ? null : dto.pid().trim();
        if (nos.isEmpty() && pid == null) throw new BadRequestException("请至少选择一条提交或一道题");
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
        if (pid != null) {
            Problem problem = requireProblem(pid);
            for (Submission s : submissions.selectList(new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getProblemId, problem.getId()).inSql(Submission::getId, inContest)
                    .orderByAsc(Submission::getId).last("FOR UPDATE")))
                found.putIfAbsent(s.getId(), s);
        }
        return reset(actor, new ArrayList<>(found.values()));
    }

    /** 题库整题重测：只动不属于任何比赛的提交，历届比赛榜单不受影响 */
    @Transactional
    public Outcome rejudgeProblem(Long actor, String pid) {
        Problem problem = requireProblem(pid);
        permissions.assertPermission(actor, ResourceType.PROBLEM, problem.getId(), PermissionOp.WRITE);
        List<Submission> targets = submissions.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getProblemId, problem.getId())
                .notInSql(Submission::getId, "SELECT submission_id FROM contest_submission")
                .orderByAsc(Submission::getId).last("FOR UPDATE"));
        return reset(actor, targets);
    }

    private Problem requireProblem(String pid) {
        Problem problem = problems.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, pid));
        if (problem == null) throw new NotFoundException("题目不存在：" + pid);
        return problem;
    }

    private static Set<String> normalize(List<String> raw) {
        Set<String> nos = new LinkedHashSet<>();
        if (raw != null) for (String no : raw) if (no != null && !no.isBlank()) nos.add(no.trim());
        if (nos.size() > MAX_SELECTED) throw new BadRequestException("一次最多选 " + MAX_SELECTED + " 条提交");
        return nos;
    }

    private List<Submission> lockByNos(Set<String> nos) {
        List<Submission> rows = submissions.selectList(new LambdaQueryWrapper<Submission>()
                .in(Submission::getSubmissionNo, nos).orderByAsc(Submission::getId).last("FOR UPDATE"));
        Set<String> seen = new HashSet<>();
        rows.forEach(s -> seen.add(s.getSubmissionNo()));
        List<String> missing = nos.stream().filter(no -> !seen.contains(no)).toList();
        if (!missing.isEmpty()) throw new BadRequestException("找不到这些提交：" + String.join("、", missing));
        return rows;
    }

    /** 正在评测：Pending/Running 且最近 STALE_MINUTES 分钟内有更新。更久的 Running 视为丢了回调，允许重测。 */
    static boolean inFlight(Submission s, LocalDateTime now) {
        boolean active = SubmissionStatus.PENDING.getStatus().equals(s.getStatus())
                || SubmissionStatus.RUNNING.getStatus().equals(s.getStatus());
        return active && (s.getUpdatedAt() == null || s.getUpdatedAt().isAfter(now.minusMinutes(RejudgeDispatcher.STALE_MINUTES)));
    }

    private Outcome reset(Long actor, List<Submission> targets) {
        if (targets.size() > MAX_WHOLE_PROBLEM)
            throw new BadRequestException("要重测的提交有 " + targets.size() + " 条，超过上限 " + MAX_WHOLE_PROBLEM + "，请按比赛或勾选分批重测");
        LocalDateTime now = LocalDateTime.now();
        List<Submission> ready = targets.stream().filter(s -> !inFlight(s, now)).toList();
        int busy = targets.size() - ready.size();
        if (ready.isEmpty()) return new Outcome(0, busy, dispatcher.pending());

        // 原来通过且未被 skip 的，先从通过数里减掉；重测回调通过时会再加回（被 skip 的回调不加）
        Map<Long, Integer> acceptDelta = new HashMap<>();
        for (Submission s : ready) {
            boolean countedAccept = SubmissionStatus.FINISHED.getStatus().equals(s.getStatus())
                    && SubmissionVerdict.ACCEPTED.getVerdict().equals(s.getVerdict())
                    && !Boolean.TRUE.equals(s.getIsSkipped());
            if (countedAccept) acceptDelta.merge(s.getProblemId(), -1, Integer::sum);
        }
        List<Long> ids = ready.stream().map(Submission::getId).toList();
        submissions.update(null, new LambdaUpdateWrapper<Submission>().in(Submission::getId, ids)
                .set(Submission::getStatus, SubmissionStatus.PENDING.getStatus())
                .set(Submission::getVerdict, null)
                .set(Submission::getScore, null)
                .set(Submission::getFinishTime, null));
        details.delete(new LambdaQueryWrapper<SubmissionDetail>().in(SubmissionDetail::getSubmissionId, ids));
        acceptDelta.forEach((problemId, d) -> problems.adjustTotalCounts(problemId, 0, d));

        List<String> nos = ready.stream().map(Submission::getSubmissionNo).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatcher.enqueue(nos); }
            });
        } else {
            dispatcher.enqueue(nos);
        }
        log.info("用户 {} 重测 {} 条提交（{} 条正在评测，未重测）", actor, nos.size(), busy);
        return new Outcome(nos.size(), busy, dispatcher.pending() + nos.size());
    }
}

package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestBalloonProblemRow;
import com.seuoj.seuojbackend.entity.ContestBalloon;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ContestBalloonMapper;
import com.seuoj.seuojbackend.mapper.ContestProblemRelMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.mapper.ContestSubmissionMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.vo.contest.ContestBalloonItemVO;
import com.seuoj.seuojbackend.vo.contest.ContestBalloonProblemVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 比赛气球队列。
 *
 * <p>判题回调和 skip 变化后对账：正式参赛者在比赛时间内第一次 AC 某题时产生一条记录，
 * 同一 (contest, user, problem) 的唯一索引保证重复 AC 不会重复入队；
 * 对应提交被重测改判或 skip 后，待送达的气球撤出队列。
 * 志愿者在气球队列页面点“送达”把记录标记为已送达。</p>
 */
@Slf4j
@Service
public class ContestBalloonService {

    /** 待送达。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已送达。 */
    public static final String STATUS_DELIVERED = "DELIVERED";

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    /**
     * 未显式配置时的默认配色，按题目顺序取用。12 种彼此可分的常见气球色，
     * 超过 12 题时循环，配色面板会提示撞色，需要手动指定。
     */
    private static final List<String> PALETTE = List.of(
            "#ef4444", "#3b82f6", "#22c55e", "#eab308",
            "#a855f7", "#f97316", "#ec4899", "#14b8a6",
            "#ffffff", "#000000", "#92400e", "#9ca3af");

    private final ContestBalloonMapper balloonMapper;
    private final ContestProblemRelMapper contestProblemRelMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final PermissionService permissionService;
    private final ContestRegisterRelMapper contestRegisterRelMapper;
    /** 对账用的独立事务：判题主事务提交之后才执行。 */
    private final TransactionTemplate requiresNew;

    public ContestBalloonService(ContestBalloonMapper balloonMapper,
                                 ContestProblemRelMapper contestProblemRelMapper,
                                 ContestSubmissionMapper contestSubmissionMapper,
                                 SubmissionMapper submissionMapper,
                                 ProblemMapper problemMapper,
                                 PermissionService permissionService,
                                 ContestRegisterRelMapper contestRegisterRelMapper,
                                 PlatformTransactionManager transactionManager) {
        this.balloonMapper = balloonMapper;
        this.contestProblemRelMapper = contestProblemRelMapper;
        this.contestSubmissionMapper = contestSubmissionMapper;
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.permissionService = permissionService;
        this.contestRegisterRelMapper = contestRegisterRelMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 判题结果或 skip 状态变化后调用：等主事务提交后，按提交所属的 (比赛, 选手, 题目) 对账。
     *
     * <p>气球是辅助功能，对账在独立事务里执行，失败只记日志，绝不回滚或阻塞判题结果。
     * 没有活动事务时立即执行。</p>
     */
    public void reconcileAfterCommit(Collection<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(new LinkedHashSet<>(submissionIds));
        Runnable task = () -> {
            for (Long id : ids) {
                try {
                    requiresNew.executeWithoutResult(status -> reconcileSubmission(id));
                } catch (RuntimeException e) {
                    log.warn("气球对账失败 submissionId={}", id, e);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** 对账单条提交对应的 (比赛, 选手, 题目)。 */
    void reconcileSubmission(Long submissionId) {
        Long contestId = contestSubmissionMapper.findContestIdBySubmissionId(submissionId);
        if (contestId == null) {
            return; // 非比赛提交
        }
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null || submission.getUserId() == null || submission.getProblemId() == null) {
            return;
        }
        reconcile(contestId, submission.getUserId(), submission.getProblemId());
    }

    /**
     * 让队列与提交现状一致：
     * <ul>
     *   <li>有合格的 AC（比赛时间内提交、未被 skip、提交者已报名且不是比赛管理者）而没有气球 → 入队；</li>
     *   <li>气球对应的提交已不合格、但有另一条合格 AC → 改挂到最早那条；</li>
     *   <li>已没有合格 AC → 待送达的气球撤出队列；已送达的保留，队列里标为“已撤销”。</li>
     * </ul>
     */
    void reconcile(Long contestId, Long userId, Long problemId) {
        Long validSubmissionId = eligibleContestant(contestId, userId)
                ? balloonMapper.selectFirstValidAccepted(contestId, userId, problemId)
                : null;
        ContestBalloon current = balloonMapper.selectOne(new LambdaQueryWrapper<ContestBalloon>()
                .eq(ContestBalloon::getContestId, contestId)
                .eq(ContestBalloon::getUserId, userId)
                .eq(ContestBalloon::getProblemId, problemId)
                .last("LIMIT 1"));

        if (validSubmissionId != null) {
            if (current == null) {
                insertBalloon(contestId, userId, problemId, validSubmissionId);
            } else if (!validSubmissionId.equals(current.getSubmissionId())) {
                balloonMapper.update(null, new LambdaUpdateWrapper<ContestBalloon>()
                        .eq(ContestBalloon::getId, current.getId())
                        .set(ContestBalloon::getSubmissionId, validSubmissionId));
            }
            return;
        }
        if (current != null && STATUS_PENDING.equals(current.getStatus())) {
            // 逻辑删除后 active_key 变为 NULL，之后再有合格 AC 可以重新入队
            balloonMapper.update(null, new LambdaUpdateWrapper<ContestBalloon>()
                    .eq(ContestBalloon::getId, current.getId())
                    .eq(ContestBalloon::getStatus, STATUS_PENDING)
                    .set(ContestBalloon::getIsDel, 1));
            log.info("气球撤出队列 contestId={}, userId={}, problemId={}", contestId, userId, problemId);
        }
    }

    /** 气球只发给正式参赛者：已报名，且不是比赛管理者（管理者验题 AC 不发）。 */
    private boolean eligibleContestant(Long contestId, Long userId) {
        Long registered = contestRegisterRelMapper.selectCount(new LambdaQueryWrapper<ContestRegisterRel>()
                .eq(ContestRegisterRel::getContestId, contestId)
                .eq(ContestRegisterRel::getUserId, userId)
                .eq(ContestRegisterRel::getIsDel, 0));
        if (registered == null || registered == 0) {
            return false;
        }
        return !permissionService.hasPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }

    private void insertBalloon(Long contestId, Long userId, Long problemId, Long submissionId) {
        ContestBalloon balloon = new ContestBalloon()
                .setContestId(contestId)
                .setSubmissionId(submissionId)
                .setUserId(userId)
                .setProblemId(problemId)
                .setColor(effectiveColors(contestId).getOrDefault(problemId, PALETTE.get(0)))
                .setStatus(STATUS_PENDING)
                .setCreatedAt(LocalDateTime.now())
                .setIsDel(0);
        try {
            balloonMapper.insert(balloon);
        } catch (DuplicateKeyException duplicate) {
            // 并发回调时另一条已入队，唯一索引兜底
            log.debug("气球已存在，忽略重复入队 contestId={}, userId={}, problemId={}", contestId, userId, problemId);
        }
    }

    /** 配色面板：题目列表及其当前生效颜色。 */
    public List<ContestBalloonProblemVO> listProblems(Long contestId) {
        assertWrite(contestId);
        List<ContestBalloonProblemRow> rows = balloonMapper.selectProblems(contestId);
        List<ContestBalloonProblemVO> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ContestBalloonProblemRow row = rows.get(i);
            ContestBalloonProblemVO vo = new ContestBalloonProblemVO();
            vo.setPid(row.getPid());
            vo.setTitle(row.getTitle());
            vo.setExplicit(isExplicit(row.getBalloonColor()));
            vo.setColor(colorAt(row.getBalloonColor(), i));
            result.add(vo);
        }
        return result;
    }

    /** 队列（含已送达，前端可自行过滤）。 */
    public List<ContestBalloonItemVO> listQueue(Long contestId) {
        assertWrite(contestId);
        // 颜色按当前配置实时计算：题目重排、增删、改色后队列都与配色面板一致
        Map<Long, String> colors = effectiveColors(contestId);
        List<ContestBalloonItemVO> queue = balloonMapper.selectQueue(contestId);
        for (ContestBalloonItemVO item : queue) {
            String color = colors.get(item.getProblemId());
            if (color != null) {
                item.setColor(color);
            }
        }
        markFirstSolves(queue);
        return queue;
    }

    /**
     * 每道题标出一血：未撤销的气球里提交时间最早的一只，同一时刻取提交编号小的。
     * 队列只含正式参赛者在比赛时间内的 AC，所以这就是本场该题的第一个通过。
     */
    static void markFirstSolves(List<ContestBalloonItemVO> queue) {
        Comparator<ContestBalloonItemVO> earliest = Comparator
                .comparing(ContestBalloonItemVO::getSubmitTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ContestBalloonItemVO::getSubmissionId, Comparator.nullsLast(Comparator.naturalOrder()));
        Map<Long, ContestBalloonItemVO> first = new HashMap<>();
        for (ContestBalloonItemVO item : queue) {
            item.setFirstSolve(false);
            if (Boolean.TRUE.equals(item.getRevoked()) || item.getSubmitTime() == null) {
                continue;
            }
            first.merge(item.getProblemId(), item, (a, b) -> earliest.compare(a, b) <= 0 ? a : b);
        }
        first.values().forEach(item -> item.setFirstSolve(true));
    }

    /** 志愿者点“送达”。 */
    public void deliver(Long contestId, Long balloonId) {
        assertWrite(contestId);
        Long userId = AuthContexts.requiredUserId();
        int updated = balloonMapper.update(null, new LambdaUpdateWrapper<ContestBalloon>()
                .eq(ContestBalloon::getId, balloonId)
                .eq(ContestBalloon::getContestId, contestId)
                .eq(ContestBalloon::getStatus, STATUS_PENDING)
                .set(ContestBalloon::getStatus, STATUS_DELIVERED)
                .set(ContestBalloon::getDeliveredAt, LocalDateTime.now())
                .set(ContestBalloon::getDeliveredBy, userId));
        if (updated == 0) {
            throw new NotFoundException("气球不存在或已送达");
        }
    }

    /** 撤回送达：已送达的气球重新回到待送达队列。 */
    public void undeliver(Long contestId, Long balloonId) {
        assertWrite(contestId);
        int updated = balloonMapper.update(null, new LambdaUpdateWrapper<ContestBalloon>()
                .eq(ContestBalloon::getId, balloonId)
                .eq(ContestBalloon::getContestId, contestId)
                .eq(ContestBalloon::getStatus, STATUS_DELIVERED)
                .set(ContestBalloon::getStatus, STATUS_PENDING)
                .set(ContestBalloon::getDeliveredAt, null)
                .set(ContestBalloon::getDeliveredBy, null));
        if (updated == 0) {
            throw new NotFoundException("气球不存在或未送达");
        }
    }

    /** 设置某道题的气球颜色；color 为空则恢复自动分配。pid 为对外题号（如 P1001）。 */
    public void setProblemColor(Long contestId, String pid, String color) {
        assertWrite(contestId);
        if (pid == null || pid.isBlank()) {
            throw new BadRequestException("题号不能为空");
        }
        String normalized = color == null ? null : color.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (normalized != null && !HEX_COLOR.matcher(normalized).matches()) {
            throw new BadRequestException("颜色格式应为 #RRGGBB");
        }
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid.trim())
                .eq(Problem::getIsDel, 0)
                .last("LIMIT 1"));
        if (problem == null) {
            throw new NotFoundException("题目不存在: " + pid);
        }
        int updated = contestProblemRelMapper.update(null, new LambdaUpdateWrapper<ContestProblemRel>()
                .eq(ContestProblemRel::getContestId, contestId)
                .eq(ContestProblemRel::getProblemId, problem.getId())
                .eq(ContestProblemRel::getIsDel, 0)
                .set(ContestProblemRel::getBalloonColor, normalized));
        if (updated == 0) {
            throw new NotFoundException("比赛中没有该题目: " + pid);
        }
        // 队列里的气球（含已送达）跟随最新配色，保证列表颜色始终与配置一致
        String effectiveColor = effectiveColors(contestId).getOrDefault(problem.getId(), PALETTE.get(0));
        balloonMapper.update(null, new LambdaUpdateWrapper<ContestBalloon>()
                .eq(ContestBalloon::getContestId, contestId)
                .eq(ContestBalloon::getProblemId, problem.getId())
                .set(ContestBalloon::getColor, effectiveColor));
    }

    /** 比赛内每道题当前生效的颜色：题目显式配置优先，否则按题目顺序取调色板。 */
    private Map<Long, String> effectiveColors(Long contestId) {
        List<ContestBalloonProblemRow> rows = balloonMapper.selectProblems(contestId);
        Map<Long, String> colors = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            colors.put(rows.get(i).getProblemId(), colorAt(rows.get(i).getBalloonColor(), i));
        }
        return colors;
    }

    private static boolean isExplicit(String color) {
        return color != null && !color.isBlank();
    }

    private static String colorAt(String explicitColor, int index) {
        return isExplicit(explicitColor) ? explicitColor : PALETTE.get(Math.floorMod(index, PALETTE.size()));
    }

    private void assertWrite(Long contestId) {
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }
}

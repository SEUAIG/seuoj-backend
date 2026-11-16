package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestBalloonProblemRow;
import com.seuoj.seuojbackend.entity.ContestBalloon;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ContestBalloonMapper;
import com.seuoj.seuojbackend.mapper.ContestProblemRelMapper;
import com.seuoj.seuojbackend.mapper.ContestSubmissionMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.vo.contest.ContestBalloonItemVO;
import com.seuoj.seuojbackend.vo.contest.ContestBalloonProblemVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 比赛气球队列。
 *
 * <p>入队由判题回调驱动：某场比赛内某选手第一次 AC 某题时产生一条记录，
 * 同一 (contest, user, problem) 的唯一索引保证重复 AC 不会重复入队。
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

    /** 未显式配置时的默认配色，按题目顺序循环取用。 */
    private static final List<String> PALETTE = List.of(
            "#ef4444", "#3b82f6", "#22c55e", "#eab308",
            "#a855f7", "#f97316", "#ec4899", "#14b8a6");

    private final ContestBalloonMapper balloonMapper;
    private final ContestProblemRelMapper contestProblemRelMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final PermissionService permissionService;

    public ContestBalloonService(ContestBalloonMapper balloonMapper,
                                 ContestProblemRelMapper contestProblemRelMapper,
                                 ContestSubmissionMapper contestSubmissionMapper,
                                 SubmissionMapper submissionMapper,
                                 ProblemMapper problemMapper,
                                 PermissionService permissionService) {
        this.balloonMapper = balloonMapper;
        this.contestProblemRelMapper = contestProblemRelMapper;
        this.contestSubmissionMapper = contestSubmissionMapper;
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.permissionService = permissionService;
    }

    /**
     * 判题回调钩子：判为 Accepted 后尝试入队。
     *
     * <p>调用方必须自行 try/catch——气球是辅助功能，任何失败都不能影响判题结果。</p>
     *
     * @param submissionId 已判为 Accepted 的提交主键
     */
    public void enqueueOnAccepted(Long submissionId) {
        Long contestId = contestSubmissionMapper.findContestIdBySubmissionId(submissionId);
        if (contestId == null) {
            return; // 非比赛提交
        }
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null || submission.getUserId() == null || submission.getProblemId() == null) {
            return;
        }
        Long existing = balloonMapper.selectCount(new LambdaQueryWrapper<ContestBalloon>()
                .eq(ContestBalloon::getContestId, contestId)
                .eq(ContestBalloon::getUserId, submission.getUserId())
                .eq(ContestBalloon::getProblemId, submission.getProblemId()));
        if (existing != null && existing > 0) {
            return; // 该选手该题已经入过队
        }

        ContestBalloon balloon = new ContestBalloon()
                .setContestId(contestId)
                .setSubmissionId(submissionId)
                .setUserId(submission.getUserId())
                .setProblemId(submission.getProblemId())
                .setColor(resolveColor(contestId, submission.getProblemId()))
                .setStatus(STATUS_PENDING)
                .setCreatedAt(LocalDateTime.now())
                .setIsDel(0);
        try {
            balloonMapper.insert(balloon);
        } catch (DuplicateKeyException duplicate) {
            // 并发 AC 时另一条提交已入队，唯一索引兜底
            log.debug("气球已存在，忽略重复入队 contestId={}, userId={}, problemId={}",
                    contestId, submission.getUserId(), submission.getProblemId());
        }
    }

    /** 配色面板：题目列表及其当前生效颜色。 */
    public List<ContestBalloonProblemVO> listProblems(Long contestId) {
        assertWrite(contestId);
        List<ContestBalloonProblemRow> rows = balloonMapper.selectProblems(contestId);
        List<ContestBalloonProblemVO> result = new ArrayList<>(rows.size());
        int index = 0;
        for (ContestBalloonProblemRow row : rows) {
            boolean explicit = row.getBalloonColor() != null && !row.getBalloonColor().isBlank();
            ContestBalloonProblemVO vo = new ContestBalloonProblemVO();
            vo.setPid(row.getPid());
            vo.setTitle(row.getTitle());
            vo.setExplicit(explicit);
            vo.setColor(explicit ? row.getBalloonColor() : PALETTE.get(Math.floorMod(index, PALETTE.size())));
            index++;
            result.add(vo);
        }
        return result;
    }

    /** 队列（含已送达，前端可自行过滤）。 */
    public List<ContestBalloonItemVO> listQueue(Long contestId) {
        assertWrite(contestId);
        return balloonMapper.selectQueue(contestId);
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
        String effectiveColor = resolveColor(contestId, problem.getId());
        balloonMapper.update(null, new LambdaUpdateWrapper<ContestBalloon>()
                .eq(ContestBalloon::getContestId, contestId)
                .eq(ContestBalloon::getProblemId, problem.getId())
                .set(ContestBalloon::getColor, effectiveColor));
    }

    /** 题目配置的颜色优先，否则按题目顺序取调色板。 */
    private String resolveColor(Long contestId, Long problemId) {
        ContestProblemRel rel = contestProblemRelMapper.selectOne(new LambdaQueryWrapper<ContestProblemRel>()
                .eq(ContestProblemRel::getContestId, contestId)
                .eq(ContestProblemRel::getProblemId, problemId)
                .eq(ContestProblemRel::getIsDel, 0)
                .last("LIMIT 1"));
        if (rel != null && rel.getBalloonColor() != null && !rel.getBalloonColor().isBlank()) {
            return rel.getBalloonColor();
        }
        List<ContestProblemRel> ordered = contestProblemRelMapper.selectList(
                new LambdaQueryWrapper<ContestProblemRel>()
                        .eq(ContestProblemRel::getContestId, contestId)
                        .eq(ContestProblemRel::getIsDel, 0)
                        .orderByAsc(ContestProblemRel::getSortOrder));
        int index = 0;
        for (int i = 0; i < ordered.size(); i++) {
            if (problemId.equals(ordered.get(i).getProblemId())) {
                index = i;
                break;
            }
        }
        return PALETTE.get(Math.floorMod(index, PALETTE.size()));
    }

    private void assertWrite(Long contestId) {
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }
}

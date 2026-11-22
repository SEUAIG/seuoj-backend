package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.common.SubmitExecStatus;
import com.seuoj.seuojbackend.dto.judge.JudgeResultDTO;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionDetailMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class JudgeService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionDetailMapper submissionDetailMapper;
    private final ProblemMapper problemMapper;
    private final ContestBalloonService balloonService;

    public JudgeService(SubmissionMapper submissionMapper,
                        SubmissionDetailMapper submissionDetailMapper,
                        ProblemMapper problemMapper,
                        ContestBalloonService balloonService) {
        this.submissionMapper = submissionMapper;
        this.submissionDetailMapper = submissionDetailMapper;
        this.problemMapper = problemMapper;
        this.balloonService = balloonService;
    }

    /**
     * 处理评测结果回调
     *
     * @param dto 评测结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleJudgeResult(JudgeResultDTO dto, String submissionNo) {
        // 回调来源校验由控制器层密钥校验中间逻辑保障

        Submission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<Submission>()
                        .select(Submission::getId, Submission::getStatus, Submission::getProblemId)
                        .eq(Submission::getSubmissionNo, submissionNo));

        if (submission == null) {
            throw new NotFoundException("提交记录不存在: " + submissionNo);
        }

        String verdict;
        if (!SubmitExecStatus.SUCCESS.equals(dto.getStatus())) {
            verdict = dto.getStatus().getStatus();
        } else {
            List<JudgeResultDetailItem> resultDetail = dto.getResultDetail();
            if (resultDetail == null || resultDetail.isEmpty()) {
                throw new BadRequestException("成功结果必须包含 resultDetail");
            }
            resultDetail.sort(Comparator.comparingInt(JudgeResultDetailItem::getId));
            verdict = deriveVerdict(resultDetail);
        }

        List<String> modifiableStatusStrs = SubmissionStatus.getModifiableStatusStrs();
        Submission updateEntity = new Submission()
                .setStatus(SubmissionStatus.FINISHED.getStatus())
                .setVerdict(verdict)
                .setFinishTime(LocalDateTime.now())
                .setScore(dto.getScore());
        if (dto.getJudgeHost() != null && !dto.getJudgeHost().isBlank()) {
            String host = dto.getJudgeHost().trim();
            updateEntity.setJudgeHost(host.length() > 32 ? host.substring(0, 32) : host);
            if (dto.getJudgeStartedAt() != null) {
                updateEntity.setJudgeStartedAt(LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(dto.getJudgeStartedAt()), java.time.ZoneId.systemDefault()));
            }
            if (dto.getJudgeMs() != null) {
                updateEntity.setJudgeMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, dto.getJudgeMs())));
            }
        }

        int updatedRows = submissionMapper.update(updateEntity, new LambdaUpdateWrapper<Submission>()
                .eq(Submission::getSubmissionNo, submissionNo)
                .in(Submission::getStatus, modifiableStatusStrs));

        if (updatedRows == 0) {
            log.info("忽略重复或不可修改状态的评测回调，submissionNo={}, currentStatus={}",
                    submissionNo, submission.getStatus());
            return;
        }

        SubmissionDetail detail = new SubmissionDetail()
                .setSubmissionId(submission.getId())
                .setResultDetail(dto.getResultDetail())
                .setSubtasks(dto.getSubtasks())
                .setErrorDetail(dto.getErrorDetail());
        submissionDetailMapper.insertOrUpdate(detail);

        if (SubmissionVerdict.ACCEPTED.getVerdict().equals(verdict) && !isSkipped(submission.getId())) {
            problemMapper.atomicallyIncreaseTotalAcceptCount(submission.getProblemId());
        }
        try {
            // 任何判定都要对账：AC 可能入队，重测改判可能撤出。对账在本事务提交后独立执行，
            // 气球只是比赛的辅助功能，任何异常都不能影响判题结果落库
            balloonService.reconcileAfterCommit(List.of(submission.getId()));
        } catch (RuntimeException balloonError) {
            log.warn("登记气球对账失败 submissionId={}", submission.getId(), balloonError);
        }
    }

    /** 评测期间被 skip 的提交不再计入通过数（skip 时已从提交数里扣掉，这里不能再加通过数）。 */
    private boolean isSkipped(Long submissionId) {
        Submission current = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
                .select(Submission::getId, Submission::getIsSkipped)
                .eq(Submission::getId, submissionId)
                .last("FOR UPDATE"));
        return current != null && Boolean.TRUE.equals(current.getIsSkipped());
    }

    /**
     * 从逐点结果推导整体判定。
     * <p>
     * fail-fast 会把首个失败点之后的测试点标成 Skipped，它们没有跑过，不能参与判定，
     * 否则会把真实失败类型覆盖成 Skipped。出现 Skipped 却没有任何失败点属于结果不完整，
     * 直接报错而不是静默当作 AC。
     */
    static String deriveVerdict(List<JudgeResultDetailItem> sortedDetails) {
        int skipped = 0;
        for (JudgeResultDetailItem item : sortedDetails) {
            if (SubmissionVerdict.SKIPPED.equals(item.getType())) {
                skipped++;
                continue;
            }
            if (!SubmissionVerdict.ACCEPTED.equals(item.getType())) {
                return item.getType().getVerdict();
            }
        }
        if (skipped > 0) {
            throw new BadRequestException("评测结果不完整：存在未运行的测试点，但没有失败测试点");
        }
        return SubmissionVerdict.ACCEPTED.getVerdict();
    }
}

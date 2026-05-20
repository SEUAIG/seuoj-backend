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

    public JudgeService(SubmissionMapper submissionMapper,
                        SubmissionDetailMapper submissionDetailMapper,
                        ProblemMapper problemMapper) {
        this.submissionMapper = submissionMapper;
        this.submissionDetailMapper = submissionDetailMapper;
        this.problemMapper = problemMapper;
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
            verdict = SubmissionVerdict.ACCEPTED.getVerdict();
            for (JudgeResultDetailItem item : resultDetail) {
                if (!SubmissionVerdict.ACCEPTED.equals(item.getType())) {
                    verdict = item.getType().getVerdict();
                    break;
                }
            }
        }

        List<String> modifiableStatusStrs = SubmissionStatus.getModifiableStatusStrs();
        Submission updateEntity = new Submission()
                .setStatus(SubmissionStatus.FINISHED.getStatus())
                .setVerdict(verdict)
                .setFinishTime(LocalDateTime.now())
                .setScore(dto.getScore());

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

        if (SubmissionVerdict.ACCEPTED.getVerdict().equals(verdict)) {
            problemMapper.atomicallyIncreaseTotalAcceptCount(submission.getProblemId());
        }
    }
}

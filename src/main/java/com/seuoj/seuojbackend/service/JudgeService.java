package com.seuoj.seuojbackend.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.common.SubmitExecStatus;
import com.seuoj.seuojbackend.dto.judge.JudgeResultDTO;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.model.vo.JudgeResultDetailItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class JudgeService {

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final ObjectMapper objectMapper;

    public JudgeService(SubmissionMapper submissionMapper, ProblemMapper problemMapper, ObjectMapper objectMapper) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理评测结果回调
     *
     * @param dto 评测结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleJudgeResult(JudgeResultDTO dto, String submissionNo) {
        // TODO:检验请求来源

        Submission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<Submission>()
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
            resultDetail.sort(Comparator.comparingInt(JudgeResultDetailItem::getCnt));
            verdict = SubmissionVerdict.ACCEPTED.getVerdict();
            for (JudgeResultDetailItem item : resultDetail) {
                if (!SubmissionVerdict.ACCEPTED.equals(item.getType())) {
                    verdict = item.getType().getVerdict();
                    break;
                }
            }
        }

        // 手动将 resultDetail 序列化为 JSON 字符串
        // 因为 LambdaUpdateWrapper.set() 不会自动应用 @TableField 上的 typeHandler
        String resultDetailJson = null;
        if (dto.getResultDetail() != null) {
            try {
                resultDetailJson = objectMapper.writeValueAsString(dto.getResultDetail());
            } catch (JsonProcessingException e) {
                log.error("序列化 resultDetail 失败, submissionNo={}", submissionNo, e);
                throw new InternalServerException("评测结果处理失败");
            }
        }

        List<String> modifiableStatusStrs = SubmissionStatus.getModifiableStatusStrs();
        int updatedRows = submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                .set(Submission::getStatus, SubmissionStatus.FINISHED.getStatus())
                .set(Submission::getVerdict, verdict)
                .set(Submission::getResultDetail, resultDetailJson)
                .set(Submission::getErrorDetail, dto.getErrorDetail())
                .set(Submission::getFinishTime, LocalDateTime.now())
                .eq(Submission::getSubmissionNo, submissionNo)
                .in(Submission::getStatus, modifiableStatusStrs));

        // 并发或重复回调场景下，状态未成功迁移则直接忽略
        if (updatedRows == 0) {
            log.info("忽略重复或不可修改状态的评测回调，submissionNo={}, currentStatus={}",
                    submissionNo, submission.getStatus());
            return;
        }

        // 仅在本次成功迁移到 Finished 且判定为 AC 时累计通过数
        if (SubmissionVerdict.ACCEPTED.getVerdict().equals(verdict)) {
            problemMapper.atomicallyIncreaseTotalAcceptCount(submission.getProblemId());
        }
    }

}

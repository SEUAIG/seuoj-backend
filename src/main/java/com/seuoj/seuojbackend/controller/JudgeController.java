package com.seuoj.seuojbackend.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.judge.JudgeResultDTO;
import com.seuoj.seuojbackend.service.JudgeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/judge")
public class JudgeController {

    private final JudgeService judgeService;

    public JudgeController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    /**
     * 接收评测结果回调
     * <p>
     * 注意：使用 @AllowAnonymous 是因为评测服务没有 JWT Token
     * 安全性通过 secretKey 验证保证
     */
    @AllowAnonymous
    // 为了测试，开放该接口权限，实际评测需要安全验证
    @PutMapping("/submission/{submissionNo}")
    public Result<Void> handleCallback(@PathVariable String submissionNo, @Valid @RequestBody JudgeResultDTO dto) {

        judgeService.handleJudgeResult(dto, submissionNo);
        return Result.onlySuccess("评测结果已更新");
    }
}

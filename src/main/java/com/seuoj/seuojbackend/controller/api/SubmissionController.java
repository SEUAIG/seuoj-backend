package com.seuoj.seuojbackend.controller.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.service.SubmissionService;
import com.seuoj.seuojbackend.vo.submission.SubmissionResultVO;
import com.seuoj.seuojbackend.vo.submission.SubmitVO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/submission")
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public Result<SubmitVO> submit(@Valid @RequestBody SubmitDTO dto) {
        return Result.success(submissionService.submit(dto));
    }

    @GetMapping("/{submissionNo}")
    public Result<SubmissionResultVO> getResult(@PathVariable String submissionNo) {
        return Result.success(submissionService.getResult(submissionNo));
    }
}

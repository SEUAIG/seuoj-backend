package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.service.SubmissionService;
import com.seuoj.seuojbackend.vo.submission.SubmissionPageVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionResultVO;
import com.seuoj.seuojbackend.vo.submission.SubmitVO;
import jakarta.validation.Valid;

import javax.management.relation.Role;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @RequireRole({ RoleType.STUDENT, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PostMapping("/submission")
    public Result<SubmitVO> submit(@Valid @RequestBody SubmitDTO dto) {
        return Result.success(submissionService.submit(dto));
    }

    @AllowAnonymous
    @GetMapping("/submission/{submissionNo}")
    public Result<SubmissionResultVO> getResult(@PathVariable String submissionNo) {
        return Result.success(submissionService.getResult(submissionNo));
    }

    @AllowAnonymous
    @GetMapping("/submission/page")
    public Result<SubmissionPageVO> listSubmissions(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(value = "user_id", required = false) Long userId,
            @RequestParam(value = "verdict", required = false) String verdict,
            @RequestParam(value = "assignment_id", required = false) Long assignmentId,
            @RequestParam(value = "pid", required = false) String pid,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "username", required = false) String username) {
        return Result.success(submissionService.listSubmissions(current, size, userId, verdict, assignmentId, pid,
                language, username));
    }
}

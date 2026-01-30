package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.RoleType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/problem")
public class ProblemController {
    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @AllowAnonymous
    @GetMapping("/{pid}")
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable String pid) {
        return Result.success(problemService.getProblemDetail(pid));
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PatchMapping("/edit")
    public Result<Void> editProblem(@Valid @RequestBody ProblemEditDTO dto) {
        problemService.editProblem(dto);
        return Result.success();
    }

}

package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.dto.problem.ProblemPageDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;

@RestController
@RequestMapping("/api/problem")
public class ProblemController {
    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /**
     * 分页查询题目列表
     */
    @AllowAnonymous
    @GetMapping("/page")
    public Result<ProblemPageVO> getProblemPage(@Valid ProblemPageDTO dto) {
        return Result.success(problemService.getProblemPage(dto));
    }

    @AllowAnonymous
    @GetMapping("/{pid}")
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable String pid) {
        return Result.success(problemService.getProblemDetail(pid));
    }

}

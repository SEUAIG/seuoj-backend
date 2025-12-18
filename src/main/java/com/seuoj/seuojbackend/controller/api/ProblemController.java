package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/api/problem")
public class ProblemController {

    @Resource
    private ProblemService problemService;

    @AllowAnonymous
    @GetMapping("/{pid}")
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable String pid) {
        return Result.success(problemService.getProblemDetail(pid));
    }

}

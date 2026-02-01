package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import org.springframework.web.bind.annotation.*;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;

import java.util.List;

@RestController
@RequestMapping("/api/problem")
public class ProblemController {
    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /**
     * 分页查询题目列表
     *
     * @param current 当前页码，从1开始
     * @param size    每页条数
     * @param title   标题模糊搜索（可选）
     * @param tagIds  标签ID列表，多选做完全匹配（可选）
     */
    @AllowAnonymous
    @GetMapping("/page")
    public Result<ProblemPageVO> getProblemPage(
            @RequestParam Integer current,
            @RequestParam Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false, name = "tag_ids") List<Long> tagIds) {
        return Result.success(problemService.getProblemPage(current, size, title, tagIds));
    }

    @AllowAnonymous
    @GetMapping("/{pid}")
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable String pid) {
        return Result.success(problemService.getProblemDetail(pid));
    }

}

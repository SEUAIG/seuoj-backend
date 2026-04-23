package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.problemset.ProblemSetCreateDTO;
import com.seuoj.seuojbackend.dto.problemset.ProblemSetProblemEditDTO;
import com.seuoj.seuojbackend.dto.problemset.ProblemSetUpdateDTO;
import com.seuoj.seuojbackend.service.ProblemSetService;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetCreateVO;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetDetailVO;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetPageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题单接口
 */
@RestController
@Validated
@RequestMapping("/api/problem_set")
public class ProblemSetController {

    private final ProblemSetService problemSetService;

    public ProblemSetController(ProblemSetService problemSetService) {
        this.problemSetService = problemSetService;
    }

    /**
     * 创建题单
     */
    @RequireRole({RoleType.STUDENT, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping
    public Result<ProblemSetCreateVO> createProblemSet(@Valid @RequestBody ProblemSetCreateDTO dto) {
        return Result.success(problemSetService.createProblemSet(dto));
    }

    /**
     * 题单分页
     */
    @AllowAnonymous
    @GetMapping("/page")
    public Result<ProblemSetPageVO> getProblemSetPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(problemSetService.getProblemSetPage(current, size));
    }

    /**
     * 题单详情
     */
    @AllowAnonymous
    @GetMapping("/{problemSetId}")
    public Result<ProblemSetDetailVO> getProblemSetDetail(@PathVariable("problemSetId") Long problemSetId) {
        return Result.success(problemSetService.getProblemSetDetail(problemSetId));
    }

    /**
     * 更新题单基础信息
     */
    @RequireRole({RoleType.STUDENT, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PutMapping("/{problemSetId}")
    public Result<Void> updateProblemSet(@PathVariable("problemSetId") Long problemSetId,
                                         @Valid @RequestBody ProblemSetUpdateDTO dto) {
        problemSetService.updateProblemSet(problemSetId, dto);
        return Result.success();
    }

    /**
     * 删除题单
     */
    @RequireRole({RoleType.STUDENT, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @DeleteMapping("/{problemSetId}")
    public Result<Void> deleteProblemSet(@PathVariable("problemSetId") Long problemSetId) {
        problemSetService.deleteProblemSet(problemSetId);
        return Result.success();
    }

    /**
     * 全量覆盖题单题目列表
     */
    @RequireRole({RoleType.STUDENT, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/{problemSetId}/problem")
    public Result<Void> replaceProblemSetProblems(@PathVariable("problemSetId") Long problemSetId,
                                                  @Valid @RequestBody ProblemSetProblemEditDTO dto) {
        problemSetService.replaceProblemSetProblems(problemSetId, dto);
        return Result.success();
    }
}

package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.RoleType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.service.ProblemTestcaseService;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/problem")
public class ProblemController {
    private final ProblemService problemService;
    private final ProblemTestcaseService problemTestcaseService;

    public ProblemController(ProblemService problemService, ProblemTestcaseService problemTestcaseService) {
        this.problemService = problemService;
        this.problemTestcaseService = problemTestcaseService;
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

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/testcases/{pid}")
    public Result<Void> uploadProblemTestcases(@PathVariable String pid,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam("format") String format,
                                               @RequestParam("name_rule") String nameRule) {
        problemTestcaseService.uploadProblemTestcases(pid, file, format, nameRule);
        return Result.success();
    }

}

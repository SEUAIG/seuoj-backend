package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.seuoj.seuojbackend.dto.problem.ProblemPageDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.service.ProblemTestcaseService;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;
import com.seuoj.seuojbackend.vo.problem.ProblemTestcaseMetaVO;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/problem")
public class ProblemController {
    private final ProblemService problemService;
    private final ProblemTestcaseService problemTestcaseService;

    public ProblemController(ProblemService problemService, ProblemTestcaseService problemTestcaseService) {
        this.problemService = problemService;
        this.problemTestcaseService = problemTestcaseService;
    }

    /**
     * 分页查询题目列表
     */
    @AllowAnonymous
    @GetMapping("/page")
    public Result<ProblemPageVO> getProblemPage(@Valid ProblemPageDTO dto,
                                                @RequestParam(value = "tag_ids", required = false) List<Long> tagIds) {
        if (tagIds != null) {
            dto.setTagIds(tagIds);
        }
        return Result.success(problemService.getProblemPage(dto));
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
                                               @RequestParam("name_rule") String nameRule,
                                               HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            int totalFiles = multipartRequest.getMultiFileMap().values().stream()
                    .mapToInt(List::size)
                    .sum();
            if (totalFiles > 1) {
                throw new BadRequestException("只允许上传一个压缩包");
            }
        }
        problemTestcaseService.uploadProblemTestcases(pid, file, format, nameRule);
        return Result.success();
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/data/{pid}")
    public Result<JudgeProblemDataResponse> getProblemData(@PathVariable String pid) {
        return Result.success(problemTestcaseService.getProblemTestcaseMeta(pid));
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/file/{pid}/{file_name}")
    public void getProblemFile(@PathVariable String pid,
                               @PathVariable("file_name") String fileName,
                               HttpServletResponse response) {
        problemTestcaseService.proxyProblemFile(pid, fileName, response);
    }

}

package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.discussion.PostModerationDTO;
import com.seuoj.seuojbackend.dto.discussion.SolutionSaveDTO;
import com.seuoj.seuojbackend.service.SolutionService;
import com.seuoj.seuojbackend.vo.discussion.PostDetailVO;
import com.seuoj.seuojbackend.vo.discussion.SolutionPageVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户题解：题目「题解」标签的列表，以及题解的增删改查、精选和隐藏 */
@RestController
@Validated
public class SolutionController {

    private final SolutionService solutionService;

    public SolutionController(SolutionService solutionService) {
        this.solutionService = solutionService;
    }

    @AllowAnonymous
    @GetMapping("/api/problem/{pid}/solutions")
    public Result<SolutionPageVO> list(@PathVariable("pid") String pid,
                                       @RequestParam(value = "contest_id", required = false) Long contestId,
                                       @RequestParam(value = "current", required = false) Long current,
                                       @RequestParam(value = "size", required = false) Long size) {
        return Result.success(solutionService.list(pid, contestId, current, size));
    }

    @AllowAnonymous
    @GetMapping("/api/post/{id}")
    public Result<PostDetailVO> get(@PathVariable("id") Long id,
                                    @RequestParam(value = "contest_id", required = false) Long contestId) {
        return Result.success(solutionService.get(id, contestId));
    }

    @PostMapping("/api/post")
    public Result<PostDetailVO> create(@Valid @RequestBody SolutionSaveDTO dto) {
        return Result.success(solutionService.create(dto));
    }

    @PutMapping("/api/post/{id}")
    public Result<PostDetailVO> update(@PathVariable("id") Long id, @Valid @RequestBody SolutionSaveDTO dto) {
        return Result.success(solutionService.update(id, dto));
    }

    @DeleteMapping("/api/post/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        solutionService.delete(id);
        return Result.success();
    }

    @PutMapping("/api/post/{id}/moderation")
    public Result<PostDetailVO> moderate(@PathVariable("id") Long id,
                                         @RequestParam(value = "contest_id", required = false) Long contestId,
                                         @Valid @RequestBody PostModerationDTO dto) {
        return Result.success(solutionService.moderate(id, dto, contestId));
    }
}

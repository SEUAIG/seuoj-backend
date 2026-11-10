package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.service.RejudgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 重测入口。权限与 skip 相同；整题重测只在题库（非比赛提交）或单场比赛内进行。 */
@RestController
@Validated
@RequestMapping("/api")
@RequiredArgsConstructor
public class RejudgeController {
    private final RejudgeService rejudge;

    @PostMapping("/submission/rejudge")
    public Result<RejudgeService.Outcome> rejudgeSelected(@Valid @RequestBody RejudgeService.SelectedRequest dto) {
        return Result.success(rejudge.rejudgeSelected(AuthContexts.requiredUserId(), dto));
    }

    @PostMapping("/contest/{id}/rejudge")
    public Result<RejudgeService.Outcome> rejudgeInContest(@PathVariable("id") Long id,
                                                          @Valid @RequestBody RejudgeService.ContestRequest dto) {
        return Result.success(rejudge.rejudgeInContest(AuthContexts.requiredUserId(), id, dto));
    }

    @PostMapping("/problem/{pid}/rejudge")
    public Result<RejudgeService.Outcome> rejudgeProblem(@PathVariable("pid") String pid) {
        return Result.success(rejudge.rejudgeProblem(AuthContexts.requiredUserId(), pid));
    }
}

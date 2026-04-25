package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.contest.ContestCreateDTO;
import com.seuoj.seuojbackend.dto.contest.ContestProblemEditDTO;
import com.seuoj.seuojbackend.dto.contest.ContestSubmitDTO;
import com.seuoj.seuojbackend.dto.contest.ContestUpdateDTO;
import com.seuoj.seuojbackend.service.ContestService;
import com.seuoj.seuojbackend.vo.contest.ContestCreateVO;
import com.seuoj.seuojbackend.vo.contest.ContestDetailVO;
import com.seuoj.seuojbackend.vo.contest.ContestPageVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemListInEditVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmissionDetailVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmissionPageVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmitVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@Validated
@RequestMapping("/api/contest")
public class ContestController {

    private final ContestService contestService;

    public ContestController(ContestService contestService) {
        this.contestService = contestService;
    }

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping
    public Result<ContestCreateVO> createContest(@Valid @RequestBody ContestCreateDTO dto) {
        return Result.success(contestService.createContest(dto));
    }

    @AllowAnonymous
    @GetMapping("/page")
    public Result<ContestPageVO> getContestPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(value = "title", required = false) String titleKeyword,
            @RequestParam(value = "rule_type", required = false) String ruleType,
            @RequestParam(value = "start_time_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTimeFrom,
            @RequestParam(value = "end_time_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTimeTo) {
        return Result.success(contestService.getContestPage(
                current, size, status, titleKeyword, ruleType, startTimeFrom, endTimeTo));
    }

    @AllowAnonymous
    @GetMapping("/{id}")
    public Result<ContestDetailVO> getContestDetail(@PathVariable("id") Long id) {
        return Result.success(contestService.getContestDetail(id));
    }

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PutMapping("/{id}")
    public Result<Void> updateContest(@PathVariable("id") Long id,
                                      @Valid @RequestBody ContestUpdateDTO dto) {
        contestService.updateContest(id, dto);
        return Result.success();
    }

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/{id}/problem")
    public Result<Void> replaceContestProblems(@PathVariable("id") Long id,
                                               @Valid @RequestBody ContestProblemEditDTO dto) {
        contestService.replaceContestProblems(id, dto);
        return Result.success();
    }

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/{id}/problem/list")
    public Result<ContestProblemListInEditVO> getContestProblemListForEdit(@PathVariable("id") Long id) {
        return Result.success(contestService.getContestProblemListForEdit(id));
    }

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @DeleteMapping("/{id}")
    public Result<Void> deleteContest(@PathVariable("id") Long id) {
        contestService.deleteContest(id);
        return Result.success();
    }

    @RequireRole({RoleType.STUDENT, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/register")
    public Result<Void> registerContest(@RequestParam("contest_id") Long contestId) {
        contestService.registerContest(contestId);
        return Result.success();
    }

    @RequireRole({RoleType.STUDENT, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @DeleteMapping("/register")
    public Result<Void> unregisterContest(@RequestParam("contest_id") Long contestId) {
        contestService.unregisterContest(contestId);
        return Result.success();
    }

    @RequireRole({RoleType.STUDENT, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/{id}/submission")
    public Result<ContestSubmitVO> submitContestSolution(@PathVariable("id") Long id,
                                                         @Valid @RequestBody ContestSubmitDTO dto) {
        return Result.success(contestService.submitContestSolution(id, dto));
    }

    @AllowAnonymous
    @GetMapping("/{id}/submission/page")
    public Result<ContestSubmissionPageVO> getContestSubmissionPage(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(contestService.getContestSubmissionPage(id, current, size));
    }

    @RequireRole({RoleType.STUDENT, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/{id}/submission/{submissionNo}")
    public Result<ContestSubmissionDetailVO> getContestSubmissionDetail(
            @PathVariable("id") Long id,
            @PathVariable("submissionNo") String submissionNo) {
        return Result.success(contestService.getContestSubmissionDetail(id, submissionNo));
    }

    @AllowAnonymous
    @GetMapping("/{id}/standings")
    public Result<ContestStandingsVO> getContestStandings(@PathVariable("id") Long id) {
        return Result.success(contestService.getContestStandings(id));
    }
}

package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.contest.ContestMembersDTO;
import com.seuoj.seuojbackend.service.ContestMemberService;
import com.seuoj.seuojbackend.vo.contest.ContestMembersVO;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contest/{id}/members")
public class ContestMemberController {
    private final ContestMemberService members;

    @GetMapping
    public Result<ContestMembersVO> list(@PathVariable("id") Long id) {
        return Result.success(members.list(id));
    }

    @GetMapping("/accounts/search")
    public Result<List<ContestMembersVO.AccountOption>> search(@PathVariable("id") Long id,
            @RequestParam(defaultValue = "") String q) {
        return Result.success(members.searchAccounts(id, q));
    }

    @PostMapping("/administrators")
    public Result<Void> addAdministrator(@PathVariable("id") Long id,
            @Valid @RequestBody ContestMembersDTO.Administrator dto) {
        members.addAdministrator(id, dto.userId(), dto.username());
        return Result.success();
    }

    @DeleteMapping("/administrators/{userId}")
    public Result<Void> removeAdministrator(@PathVariable("id") Long id, @PathVariable("userId") Long userId) {
        members.removeAdministrator(id, userId);
        return Result.success();
    }

    @PutMapping("/participants")
    public Result<Integer> upsertParticipants(@PathVariable("id") Long id,
            @Valid @RequestBody ContestMembersDTO.Batch dto) {
        return Result.success(members.upsertParticipants(id, dto));
    }

    @DeleteMapping("/participants/{userId}")
    public Result<Void> removeParticipant(@PathVariable("id") Long id, @PathVariable("userId") Long userId) {
        members.removeParticipant(id, userId);
        return Result.success();
    }

    @PostMapping("/participants/actions")
    public Result<Void> batchAction(@PathVariable("id") Long id, @Valid @RequestBody ContestMembersDTO.Selection dto) {
        members.batchAction(id, dto);
        return Result.success();
    }

    @PostMapping("/accounts/preview")
    public Result<List<ContestMembersVO.AccountPlan>> preview(@PathVariable("id") Long id, @Valid @RequestBody ContestMembersDTO.Generate dto) {
        return Result.success(members.previewAccounts(id, dto));
    }

    @PostMapping("/accounts/generate")
    public Result<List<ContestMembersVO.Credential>> generate(@PathVariable("id") Long id,
            @Valid @RequestBody ContestMembersDTO.Generate dto, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(members.generateAccounts(id, dto));
    }

    @PostMapping("/accounts/reset")
    public Result<List<ContestMembersVO.Credential>> reset(@PathVariable("id") Long id,
            @Valid @RequestBody ContestMembersDTO.Accounts dto, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(members.resetAccounts(id, dto));
    }

    @PutMapping("/accounts/status")
    public Result<Void> status(@PathVariable("id") Long id, @Valid @RequestBody ContestMembersDTO.Accounts dto) {
        members.setAccountsEnabled(id, dto);
        return Result.success();
    }
}

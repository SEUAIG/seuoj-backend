package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.dto.user.UpdateProfileDTO;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.service.JudgeHostService;
import com.seuoj.seuojbackend.service.RejudgeService;
import com.seuoj.seuojbackend.service.UserBanService;
import com.seuoj.seuojbackend.service.UserSettingsService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
public class AdminController {
    private final UserSettingsService settings;
    private final UserBanService bans;
    private final JudgeHostService judgeHosts;
    @PutMapping("/user/{userId}/role")
    public Result<Void> setUserRole(@PathVariable Long userId,@RequestBody Map<String,String> body) {
        Set<Capability> assigned=switch(Objects.toString(body.get("role"), "").toUpperCase(Locale.ROOT)) {
            case "STUDENT" -> Set.of();
            case "TEACHER" -> Set.of(Capability.TEACHER);
            case "ADMIN" -> Set.of(Capability.ACCOUNT_ADMIN);
            default -> throw new BadRequestException("不支持的角色");
        };
        settings.updatePermissions(AuthContexts.requiredUserId(),userId,assigned);
        return Result.success();
    }
    @PutMapping("/user/{userId}/profile")
    public Result<Void> updateUserProfile(@PathVariable Long userId,@Valid @RequestBody UpdateProfileDTO dto) {
        settings.updateProfile(AuthContexts.requiredUserId(),userId,new UserSettingsService.ProfileUpdate(dto.getNickname(),null,null));
        return Result.success();
    }
    /** 封禁：userIds 与 usernames 可同时给出，任一账号不合格则整批不生效。until 为空表示永久。 */
    @PostMapping("/users/ban")
    public Result<UserBanService.Outcome> ban(@Valid @RequestBody UserBanService.BanRequest dto) {
        return Result.success(bans.ban(AuthContexts.requiredUserId(),dto));
    }
    @PostMapping("/users/unban")
    public Result<UserBanService.Outcome> unban(@RequestBody UserBanService.UnbanRequest dto) {
        return Result.success(bans.unban(AuthContexts.requiredUserId(),dto));
    }
    /** 评测机列表、排队与计时探针（管理员可看；以下修改类接口仅超级管理员） */
    @GetMapping("/judge/hosts")
    public Result<JudgeHostService.Status> judgeHosts() {
        return Result.success(judgeHosts.status(AuthContexts.requiredUserId()));
    }
    @PostMapping("/judge/hosts")
    public Result<JudgeHostService.Status> addJudgeHost() {
        return Result.success(judgeHosts.add(AuthContexts.requiredUserId()));
    }
    @DeleteMapping("/judge/hosts/{id}")
    public Result<JudgeHostService.Status> removeJudgeHost(@PathVariable int id) {
        return Result.success(judgeHosts.remove(AuthContexts.requiredUserId(),id));
    }
    /** body: {"enabled": true|false} */
    @PutMapping("/judge/hosts/{id}/enabled")
    public Result<JudgeHostService.Status> setJudgeHostEnabled(@PathVariable int id,@RequestBody Map<String,Boolean> body) {
        return Result.success(judgeHosts.setEnabled(AuthContexts.requiredUserId(),id,requireEnabled(body)));
    }
    @PutMapping("/judge/hosts/enabled")
    public Result<JudgeHostService.Status> setAllJudgeHostsEnabled(@RequestBody Map<String,Boolean> body) {
        return Result.success(judgeHosts.setAllEnabled(AuthContexts.requiredUserId(),requireEnabled(body)));
    }
    @GetMapping("/judge/hosts/{id}/judgings")
    public Result<List<JudgeHostService.Judging>> judgeHostJudgings(@PathVariable int id) {
        return Result.success(judgeHosts.judgings(AuthContexts.requiredUserId(),id));
    }
    /** body: {"hours": n}，不给表示不限时间 */
    @PostMapping("/judge/hosts/{id}/rejudge")
    public Result<RejudgeService.Outcome> rejudgeJudgeHost(@PathVariable int id,@RequestBody(required=false) Map<String,Integer> body) {
        return Result.success(judgeHosts.rejudge(AuthContexts.requiredUserId(),id,body==null?null:body.get("hours")));
    }
    private static boolean requireEnabled(Map<String,Boolean> body) {
        Boolean enabled=body==null?null:body.get("enabled");
        if(enabled==null) throw new BadRequestException("缺少 enabled");
        return enabled;
    }
}

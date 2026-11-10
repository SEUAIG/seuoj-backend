package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.dto.user.UpdateProfileDTO;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
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
}

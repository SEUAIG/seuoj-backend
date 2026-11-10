package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.auth.SendCodeDTO;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.service.UserSettingsService;
import com.seuoj.seuojbackend.service.VerificationCodeService;
import com.seuoj.seuojbackend.vo.auth.SendCodeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/user") @RequiredArgsConstructor
public class UserSettingsController {
    private final UserSettingsService settings;
    private final VerificationCodeService codes;
    @GetMapping("/me/settings")
    public Result<UserSettingsService.Settings> me() {
        Long uid=AuthContexts.requiredUserId();return Result.success(settings.get(uid,uid));
    }
    @GetMapping("/{id}/settings")
    public Result<UserSettingsService.Settings> get(@PathVariable Long id) {
        return Result.success(settings.get(AuthContexts.requiredUserId(),id));
    }
    @PutMapping("/{id}/settings")
    public Result<UserSettingsService.Settings> profile(@PathVariable Long id,@Valid @RequestBody UserSettingsService.ProfileUpdate dto) {
        return Result.success(settings.updateProfile(AuthContexts.requiredUserId(),id,dto));
    }
    @PutMapping("/{id}/permissions")
    public Result<UserSettingsService.Settings> permissions(@PathVariable Long id,@Valid @RequestBody UserSettingsService.PermissionsUpdate dto) {
        return Result.success(settings.updatePermissions(AuthContexts.requiredUserId(),id,dto.assigned()));
    }
    @PostMapping("/{id}/password/reset")
    public Result<UserSettingsService.PasswordReset> resetPassword(@PathVariable Long id) {
        return Result.success(settings.resetPassword(AuthContexts.requiredUserId(),id));
    }
    @PostMapping("/me/email/code")
    public Result<SendCodeVO> emailCode(@Valid @RequestBody SendCodeDTO dto) {
        AuthContexts.requiredUserId(); return Result.success(codes.sendCode(dto));
    }
    @PutMapping("/me/email")
    public Result<UserSettingsService.Settings> email(@Valid @RequestBody UserSettingsService.EmailUpdate dto) {
        return Result.success(settings.updateEmail(AuthContexts.requiredUserId(),dto));
    }
}

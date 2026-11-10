package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.user.UpdateProfileDTO;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.service.AuthService;
import com.seuoj.seuojbackend.service.UserSettingsService;
import com.seuoj.seuojbackend.vo.user.UserMeVO;
import com.seuoj.seuojbackend.vo.user.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;
    private final UserSettingsService settings;

    public UserController(AuthService authService, UserSettingsService settings) {
        this.authService = authService;
        this.settings = settings;
    }

    @GetMapping("/me")
    public Result<UserMeVO> me() {
        return Result.success(authService.getCurrentUserProfile(AuthContexts.requiredUserId()));
    }

    @PutMapping("/me/profile")
    public Result<UserMeVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.success(authService.updateProfile(AuthContexts.requiredUserId(), dto));
    }

    @GetMapping("/profile/{userId}")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long userId) {
        Long actor = AuthContexts.requiredUserId();
        UserProfileVO profile = authService.getUserProfile(actor, userId);
        profile.setCanManageProfile(settings.canManageProfile(actor, userId));
        return Result.success(profile);
    }
}

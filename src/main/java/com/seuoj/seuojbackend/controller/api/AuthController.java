package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.auth.LoginDTO;
import com.seuoj.seuojbackend.dto.auth.RegisterDTO;
import com.seuoj.seuojbackend.dto.auth.ResetPasswordDTO;
import com.seuoj.seuojbackend.dto.auth.ChangePasswordDTO;
import com.seuoj.seuojbackend.dto.auth.SendCodeDTO;
import com.seuoj.seuojbackend.service.AuthService;
import com.seuoj.seuojbackend.service.VerificationCodeService;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import com.seuoj.seuojbackend.vo.auth.SendCodeVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户身份相关控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;

    public AuthController(AuthService authService, VerificationCodeService verificationCodeService) {
        this.authService = authService;
        this.verificationCodeService = verificationCodeService;
    }

    /**
     * 登录
     */
    @AllowAnonymous
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /**
     * 注册
     */
    @AllowAnonymous
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    /**
     * 发送注册邮箱验证码
     */
    @AllowAnonymous
    @PostMapping("/register/send-code")
    public Result<SendCodeVO> sendRegisterCode(@Valid @RequestBody SendCodeDTO dto) {
        return Result.success(verificationCodeService.sendCode(dto));
    }

    /**
     * 发送密码重置验证码（复用验证码服务）
     */
    @AllowAnonymous
    @PostMapping("/reset-password/send-code")
    public Result<SendCodeVO> sendResetCode(@Valid @RequestBody SendCodeDTO dto) {
        return Result.success(verificationCodeService.sendCode(dto));
    }

    /**
     * 重置密码
     */
    @AllowAnonymous
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    /**
     * 修改密码（已登录用户）
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        authService.changePassword(userId, dto);
        return Result.success();
    }

}

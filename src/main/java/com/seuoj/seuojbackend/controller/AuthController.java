package com.seuoj.seuojbackend.controller;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.auth.LoginDTO;
import com.seuoj.seuojbackend.service.AuthService;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private AuthService authService;
    /**
     * 登录
     */
    @AllowAnonymous
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO  dto) {
        return Result.success(authService.login(dto));
    }

}

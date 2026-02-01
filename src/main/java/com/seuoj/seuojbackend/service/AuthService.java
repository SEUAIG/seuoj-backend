package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seuoj.seuojbackend.dto.auth.LoginDTO;
import com.seuoj.seuojbackend.dto.auth.RegisterDTO;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户身份相关服务
 */
@Service
public class AuthService {

    private final UserInfoMapper userInfoMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;

    public AuthService(UserInfoMapper userInfoMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
            VerificationCodeService verificationCodeService) {
        this.userInfoMapper = userInfoMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.verificationCodeService = verificationCodeService;
    }

    /**
     * 用户注册（邮箱验证码确认）
     */
    public void register(RegisterDTO dto) {

        // 检查用户名是否已存在
        if (userInfoMapper.selectOne(new QueryWrapper<UserInfo>().eq("username", dto.getUsername())) != null) {
            throw new ConflictException("用户名已存在");
        }

        // 检查邮箱是否已存在
        if (userInfoMapper.selectOne(new QueryWrapper<UserInfo>().eq("email", dto.getEmail())) != null) {
            throw new ConflictException("邮箱已被注册");
        }
        // 验证验证码
        String verifiedEmail = verificationCodeService.verifyCode(dto.getVerificationId(), dto.getCode());
        if (verifiedEmail == null) {
            throw new BadRequestException("验证码无效或已过期");
        }

        // 验证邮箱是否与验证码对应的邮箱一致
        if (!verifiedEmail.equals(dto.getEmail())) {
            throw new BadRequestException("邮箱与验证码不匹配");
        }

        UserInfo newUser = new UserInfo();
        newUser.setUsername(dto.getUsername());
        newUser.setEmail(dto.getEmail());
        // 使用 passwordEncoder 加密密码
        newUser.setPassword(passwordEncoder.encode(dto.getPassword()));

        userInfoMapper.insert(newUser);
    }

    /**
     * 用户登录（使用邮箱+密码登录）
     */
    public LoginVO login(LoginDTO dto) {
        UserInfo user = userInfoMapper.selectOne(new QueryWrapper<UserInfo>().eq("email", dto.getEmail()));
        if (user == null) {
            throw new AuthorizationException("邮箱错误");
        }

        boolean isPasswordMatch = passwordEncoder.matches(dto.getPassword(), user.getPassword());

        if (!isPasswordMatch) {
            throw new AuthorizationException("密码错误");
        }

        String token = jwtUtil.createToken(user.getId());

        LoginVO loginVO = new LoginVO();
        loginVO.setJwt(token);

        return loginVO;
    }
}

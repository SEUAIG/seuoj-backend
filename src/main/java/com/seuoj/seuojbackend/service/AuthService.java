package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seuoj.seuojbackend.dto.auth.LoginDTO;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户身份相关服务
 */
@Service
public class AuthService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户注册（测试用，非正式）
     *
     * @param username    用户名
     * @param rawPassword 明文密码
     */
    public void register(String username, String rawPassword) {
        // 检查用户名是否已存在
        if (userInfoMapper.selectOne(new QueryWrapper<UserInfo>().eq("username", username)) != null) {
            throw new ConflictException("用户名已存在");
        }

        UserInfo newUser = new UserInfo();
        newUser.setUsername(username);
        // 使用 passwordEncoder 加密密码
        newUser.setPassword(passwordEncoder.encode(rawPassword));

        userInfoMapper.insert(newUser);
    }


    /**
     * 用户登录（暂定用户名唯一，使用用户名+密码登录）
     */
    public LoginVO login(LoginDTO dto) {
        UserInfo user = userInfoMapper.selectOne(new QueryWrapper<UserInfo>().eq("username", dto.getUsername()));
        if (user == null) {
            throw new AuthorizationException("用户名或密码错误");
        }

        boolean isPasswordMatch = passwordEncoder.matches(dto.getPassword(), user.getPassword());

        if (!isPasswordMatch) {
            throw new AuthorizationException("用户名或密码错误");
        }

        String token = jwtUtil.createToken(user.getId());

        LoginVO loginVO = new LoginVO();
        loginVO.setJwt(token);

        return loginVO;
    }
}

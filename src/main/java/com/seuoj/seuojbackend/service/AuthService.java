package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.auth.LoginDTO;
import com.seuoj.seuojbackend.dto.auth.RegisterDTO;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 用户身份相关服务
 */
@Service
public class AuthService {

    private final UserInfoMapper userInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;

    public AuthService(UserInfoMapper userInfoMapper, UserRoleMapper userRoleMapper, UserRoleRelMapper userRoleRelMapper,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil, VerificationCodeService verificationCodeService) {
        this.userInfoMapper = userInfoMapper;
        this.userRoleMapper = userRoleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.verificationCodeService = verificationCodeService;
    }

    /**
     * 用户注册（邮箱验证码确认）
     */
    @Transactional
    public void register(RegisterDTO dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());

        // TODO: 是否需要考虑枚举风险？
        // 检查邮箱是否已存在
        if (userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getEmail, normalizedEmail)) != null) {
            throw new ConflictException("邮箱已被注册");
        }

        // 预校验验证码并预留，注册失败时可复用
        String verifiedEmail = verificationCodeService.preVerifyCode(dto.getVerificationId(), dto.getCode());
        if (verifiedEmail == null) {
            throw new BadRequestException(ErrorCode.CODE_INVALID_OR_EXPIRED.getCode(), "验证码无效或已过期");
        }

        // 事务成功提交之后再进行验证码缓存的删除
        boolean hasTx = TransactionSynchronizationManager.isActualTransactionActive();
        if (hasTx) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    verificationCodeService.consumeVerification(dto.getVerificationId());
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        verificationCodeService.releaseReservation(dto.getVerificationId());
                    }
                }
            });
        }

        boolean success = false;
        try {
            // 验证邮箱是否与验证码对应的邮箱一致
            if (!verifiedEmail.equals(normalizedEmail)) {
                throw new BadRequestException("邮箱与验证码不匹配");
            }

            UserInfo newUser = new UserInfo();
            newUser.setUsername(dto.getUsername());
            newUser.setEmail(normalizedEmail);
            // 使用 passwordEncoder 加密密码
            newUser.setPassword(passwordEncoder.encode(dto.getPassword()));

            try {
                userInfoMapper.insert(newUser);
            } catch (DuplicateKeyException e) {
                throw new ConflictException("邮箱已被注册");
            }

            UserRole defaultRole = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getRoleCode, RoleType.USER.getCode())
                    .eq(UserRole::getIsDel, 0));
            if (defaultRole == null) {
                throw new BadRequestException("默认角色 USER 不存在");
            }

            UserRoleRel rel = new UserRoleRel();
            rel.setUserId(newUser.getId());
            rel.setRoleId(defaultRole.getId());
            userRoleRelMapper.insert(rel);

            success = true;
        } finally {
            if (!hasTx) {
                if (success) {
                    verificationCodeService.consumeVerification(dto.getVerificationId());
                } else {
                    verificationCodeService.releaseReservation(dto.getVerificationId());
                }
            }
        }
    }

    /**
     * 用户登录（使用邮箱+密码登录）
     */
    public LoginVO login(LoginDTO dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());
        UserInfo user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getEmail, normalizedEmail));
        if (user == null) {
            throw new AuthorizationException("密码或邮箱错误");
        }

        boolean isPasswordMatch = passwordEncoder.matches(dto.getPassword(), user.getPassword());

        if (!isPasswordMatch) {
            throw new AuthorizationException("密码或邮箱错误");
        }

        String token = jwtUtil.createToken(user.getId());

        LoginVO loginVO = new LoginVO();
        loginVO.setJwt(token);

        return loginVO;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}

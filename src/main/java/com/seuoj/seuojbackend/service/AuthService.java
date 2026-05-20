package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.auth.LoginDTO;
import com.seuoj.seuojbackend.dto.auth.RegisterDTO;
import com.seuoj.seuojbackend.dto.auth.ResetPasswordDTO;
import com.seuoj.seuojbackend.dto.auth.ChangePasswordDTO;
import com.seuoj.seuojbackend.dto.admin.BatchImportUserDTO;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.util.JwtTokenType;
import com.seuoj.seuojbackend.util.JwtUtil;
import com.seuoj.seuojbackend.vo.admin.BatchImportResultVO;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import com.seuoj.seuojbackend.vo.auth.TokenExchangeVO;
import com.seuoj.seuojbackend.vo.user.UserMeVO;
import com.seuoj.seuojbackend.vo.user.UserProfileVO;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class AuthService {

    private final UserInfoMapper userInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public AuthService(UserInfoMapper userInfoMapper, UserRoleMapper userRoleMapper, UserRoleRelMapper userRoleRelMapper,
                       UserRoleService userRoleService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       VerificationCodeService verificationCodeService, JavaMailSender mailSender) {
        this.userInfoMapper = userInfoMapper;
        this.userRoleMapper = userRoleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.userRoleService = userRoleService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.verificationCodeService = verificationCodeService;
        this.mailSender = mailSender;
    }

    /**
     * 用户注册（邮箱验证码确认）
     */
    @Transactional
    public void register(RegisterDTO dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());

        // 对外返回统一提示，避免暴露邮箱是否已注册的细节
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
                    .eq(UserRole::getRoleCode, RoleType.STUDENT.getCode())
                    .eq(UserRole::getIsDel, 0));
            if (defaultRole == null) {
                throw new BadRequestException("默认角色 STUDENT 不存在");
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

    public LoginVO login(LoginDTO dto) {
        String identifier = dto.getIdentifier().trim();
        UserInfo user;
        if (identifier.contains("@")) {
            String normalizedEmail = normalizeEmail(identifier);
            user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getEmail, normalizedEmail));
        } else {
            user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUsername, identifier));
        }
        if (user == null) {
            throw new AuthorizationException("用户名/邮箱或密码错误");
        }

        boolean isPasswordMatch = passwordEncoder.matches(dto.getPassword(), user.getPassword());

        if (!isPasswordMatch) {
            throw new AuthorizationException("用户名/邮箱或密码错误");
        }

        String token = jwtUtil.createAccessToken(String.valueOf(user.getId()));

        LoginVO loginVO = new LoginVO();
        loginVO.setJwt(token);
        loginVO.setUsername(user.getUsername());
        loginVO.setNickname(user.getNickname());
        loginVO.setRole(userRoleService.getHighestRoleLabel(user.getId()));

        return loginVO;
    }

    public TokenExchangeVO exchangeToken(String token) {
        JwtUtil.ParsedToken parsedToken = jwtUtil.parseToken(token);
        if (parsedToken == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "令牌无效或已过期");
        }

        UserInfo user;
        try {
            user = userInfoMapper.selectById(Long.parseLong(parsedToken.subject()));
        } catch (NumberFormatException e) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "令牌无效或已过期");
        }
        if (user == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "用户不存在");
        }

        String userIdStr = String.valueOf(user.getId());
        TokenExchangeVO response = new TokenExchangeVO();
        if (parsedToken.tokenType() == JwtTokenType.TEMP) {
            response.setAccessToken(jwtUtil.createAccessToken(userIdStr));
            return response;
        }

        if (parsedToken.tokenType() == JwtTokenType.ACCESS) {
            response.setTempToken(jwtUtil.createTempToken(userIdStr));
            return response;
        }

        throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "不支持的令牌类型");
    }

    public UserMeVO getCurrentUserProfile(Long userId) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "用户不存在");
        }

        UserMeVO profile = new UserMeVO();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setNickname(user.getNickname());
        profile.setEmail(user.getEmail());
        return profile;
    }

    public UserProfileVO getUserProfile(Long currentUserId, Long targetUserId) {
        if (!Objects.equals(currentUserId, targetUserId) && !userRoleService.isTeacherOrAdmin(currentUserId)) {
            throw new ForbiddenException("无权查看该用户资料");
        }
        UserInfo user = userInfoMapper.selectById(targetUserId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(userRoleService.getHighestRoleLabel(targetUserId));
        return vo;
    }

    public UserMeVO updateProfile(Long userId, com.seuoj.seuojbackend.dto.user.UpdateProfileDTO dto) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        if (dto.getNickname() != null) {
            String nickname = dto.getNickname().trim();
            user.setNickname(nickname.isEmpty() ? null : nickname);
        }
        userInfoMapper.updateById(user);
        return getCurrentUserProfile(userId);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /**
     * 批量导入用户（管理员操作，跳过邮箱验证）
     */
    public BatchImportResultVO batchImportUsers(BatchImportUserDTO dto) {
        List<BatchImportUserDTO.UserRow> users = dto.getUsers();
        boolean isRandomMode = "random".equals(dto.getPasswordMode());
        boolean shouldSendEmail = dto.isSendEmail();

        BatchImportResultVO result = new BatchImportResultVO();
        result.setTotalCount(users.size());

        UserRole defaultRole = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleCode, RoleType.STUDENT.getCode())
                .eq(UserRole::getIsDel, 0));
        if (defaultRole == null) {
            throw new BadRequestException("默认角色 STUDENT 不存在");
        }

        // 前置校验密码模式一致性
        for (int i = 0; i < users.size(); i++) {
            BatchImportUserDTO.UserRow row = users.get(i);
            String pwd = row.getPassword();
            boolean hasPwd = pwd != null && !pwd.trim().isEmpty();
            if (!isRandomMode && !hasPwd) {
                result.getFailures().add(new BatchImportResultVO.FailDetail(
                        i + 1, row.getUsername(), row.getEmail(), "指定密码模式下密码不能为空"));
            }
            if (isRandomMode && hasPwd) {
                result.getFailures().add(new BatchImportResultVO.FailDetail(
                        i + 1, row.getUsername(), row.getEmail(), "随机密码模式下不应提供密码"));
            }
        }
        if (!result.getFailures().isEmpty()) {
            result.setSuccessCount(0);
            result.setFailCount(result.getFailures().size());
            return result;
        }

        int successCount = 0;
        for (int i = 0; i < users.size(); i++) {
            BatchImportUserDTO.UserRow row = users.get(i);
            try {
                String normalizedEmail = normalizeEmail(row.getEmail());
                String username = row.getUsername().trim();

                if (normalizedEmail == null || normalizedEmail.isEmpty()) {
                    normalizedEmail = username.toLowerCase() + "@seu.edu.cn";
                }

                // 检查用户名是否已存在 → 跳过（账号复用）
                if (userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUsername, username)) != null) {
                    result.getSkipped().add(new BatchImportResultVO.SkipDetail(
                            i + 1, username, normalizedEmail, "用户名已存在，跳过"));
                    continue;
                }

                // 检查邮箱格式
                if (!normalizedEmail.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    result.getFailures().add(new BatchImportResultVO.FailDetail(
                            i + 1, username, normalizedEmail, "邮箱格式无效"));
                    continue;
                }

                // 检查邮箱是否已存在 → 跳过
                if (userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getEmail, normalizedEmail)) != null) {
                    result.getSkipped().add(new BatchImportResultVO.SkipDetail(
                            i + 1, username, normalizedEmail, "邮箱已被注册，跳过"));
                    continue;
                }

                // 确定密码
                String rawPassword = isRandomMode ? generateRandomPassword() : row.getPassword().trim();

                UserInfo newUser = new UserInfo();
                newUser.setUsername(username);
                if (row.getNickname() != null && !row.getNickname().trim().isEmpty()) {
                    newUser.setNickname(row.getNickname().trim());
                }
                newUser.setEmail(normalizedEmail);
                newUser.setPassword(passwordEncoder.encode(rawPassword));

                try {
                    userInfoMapper.insert(newUser);
                } catch (DuplicateKeyException e) {
                    result.getSkipped().add(new BatchImportResultVO.SkipDetail(
                            i + 1, username, normalizedEmail, "用户名或邮箱已存在，跳过"));
                    continue;
                }

                UserRoleRel rel = new UserRoleRel();
                rel.setUserId(newUser.getId());
                rel.setRoleId(defaultRole.getId());
                userRoleRelMapper.insert(rel);

                // 发送通知邮件（异步忽略失败，不影响导入结果）
                if (shouldSendEmail) {
                    try {
                        sendAccountNotificationEmail(normalizedEmail, username, rawPassword);
                    } catch (Exception e) {
                        log.warn("账号通知邮件发送失败: {}, {}", normalizedEmail, e.getMessage());
                    }
                }

                result.getSuccesses().add(new BatchImportResultVO.SuccessDetail(
                        i + 1, username, normalizedEmail, rawPassword));
                successCount++;
            } catch (Exception e) {
                result.getFailures().add(new BatchImportResultVO.FailDetail(
                        i + 1, row.getUsername(), row.getEmail(), "系统异常: " + e.getMessage()));
            }
        }

        result.setSuccessCount(successCount);
        result.setSkippedCount(result.getSkipped().size());
        result.setFailCount(result.getTotalCount() - successCount - result.getSkippedCount());
        return result;
    }

    /**
     * 修改密码（已登录用户，验证旧密码）
     */
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userInfoMapper.updateById(user);
    }

    /**
     * 重置密码（通过邮箱验证码）
     */
    public void resetPassword(ResetPasswordDTO dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());

        // 验证验证码
        String verifiedEmail = verificationCodeService.verifyCode(dto.getVerificationId(), dto.getCode());
        if (verifiedEmail == null) {
            throw new BadRequestException(ErrorCode.CODE_INVALID_OR_EXPIRED.getCode(), "验证码无效或已过期");
        }

        if (!verifiedEmail.equals(normalizedEmail)) {
            throw new BadRequestException("邮箱与验证码不匹配");
        }

        UserInfo user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getEmail, normalizedEmail));
        if (user == null) {
            throw new BadRequestException("该邮箱未注册");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userInfoMapper.updateById(user);
    }

    /**
     * 生成8位随机密码（包含大小写字母和数字）
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 发送账号通知邮件
     */
    private void sendAccountNotificationEmail(String to, String username, String rawPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("【SEUOJ】您的账号已创建");
        message.setText("您好，\n\n管理员已为您创建 SEUOJ 账号，请使用以下信息登录：\n\n"
                + "邮箱：" + to + "\n"
                + "用户名：" + username + "\n"
                + "初始密码：" + rawPassword + "\n\n"
                + "请登录后尽快修改密码。\n\n"
                + "—— SEUOJ 团队");
        mailSender.send(message);
    }
}

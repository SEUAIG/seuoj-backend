package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.util.JwtTokenType;
import com.seuoj.seuojbackend.util.JwtUtil;
import com.seuoj.seuojbackend.util.Totp;
import com.seuoj.seuojbackend.util.UserBans;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import com.seuoj.seuojbackend.vo.auth.MfaSetupVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 两步验证（TOTP）。超级管理员强制启用；其他角色不提供。
 * <p>
 * 登录分两段：密码通过后只发 5 分钟的 MFA 令牌，凭它验证 6 位码（或恢复码）才签发正式访问令牌。
 * 未绑定的超级管理员凭同一种令牌先绑定验证器。
 */
@Slf4j
@Service
public class MfaService {
    public static final String STEP_VERIFY = "verify";
    public static final String STEP_SETUP = "setup";

    private static final String ISSUER = "SEUOJ";
    private static final int MAX_FAILURES = 10;
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserInfoMapper userInfoMapper;
    private final UserRoleService userRoleService;
    private final JwtUtil jwtUtil;
    private final Cache<Long, AtomicInteger> failures = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    public MfaService(UserInfoMapper userInfoMapper, UserRoleService userRoleService, JwtUtil jwtUtil) {
        this.userInfoMapper = userInfoMapper;
        this.userRoleService = userRoleService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 密码已通过：需要两步验证时返回中间态，否则返回 null 由调用方直接签发访问令牌。
     */
    public LoginVO challengeIfNeeded(UserInfo user) {
        String step;
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            step = STEP_VERIFY;
        } else if (userRoleService.isSuperAdmin(user.getId())) {
            step = STEP_SETUP;
        } else {
            return null;
        }
        LoginVO vo = new LoginVO();
        vo.setMfa(step);
        vo.setMfaToken(jwtUtil.createMfaToken(user));
        return vo;
    }

    /**
     * 校验 6 位动态码或恢复码，通过后返回用户
     */
    public UserInfo verify(String mfaToken, String rawCode) {
        UserInfo user = resolve(mfaToken);
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BadRequestException("该账号尚未绑定验证器");
        }
        checkNotLocked(user.getId());

        String code = normalize(rawCode);
        boolean ok = code.matches("\\d{6}") ? acceptTotp(user, code) : acceptRecoveryCode(user, code);
        if (!ok) {
            throw fail(user.getId());
        }
        failures.invalidate(user.getId());
        return user;
    }

    /**
     * 生成待绑定的密钥。每次调用都换新密钥，旧二维码作废。
     */
    public MfaSetupVO setup(String mfaToken) {
        UserInfo user = resolve(mfaToken);
        requireSetupAllowed(user);

        String secret = Totp.generateSecret();
        int updated = userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getId, user.getId())
                .eq(UserInfo::getTotpEnabled, false)
                .set(UserInfo::getTotpSecret, secret)
                .set(UserInfo::getTotpLastStep, null)
                .set(UserInfo::getTotpRecoveryCodes, null));
        if (updated != 1) {
            throw new ConflictException("两步验证已启用，请重新登录");
        }

        MfaSetupVO vo = new MfaSetupVO();
        vo.setSecret(secret);
        vo.setOtpauthUri(Totp.otpauthUri(ISSUER, user.getUsername(), secret));
        return vo;
    }

    /**
     * 用验证器上显示的码确认绑定，返回用户和一次性恢复码明文（只出现这一次）
     */
    public EnableResult enable(String mfaToken, String rawCode) {
        UserInfo user = resolve(mfaToken);
        requireSetupAllowed(user);
        String secret = user.getTotpSecret();
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("请先获取二维码");
        }
        checkNotLocked(user.getId());

        long step = Totp.matchStep(secret, normalize(rawCode), Totp.currentStep(), null);
        if (step < 0) {
            throw fail(user.getId());
        }

        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> hashes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            codes.add(code);
            hashes.add(sha256(normalize(code)));
        }

        int updated = userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getId, user.getId())
                .eq(UserInfo::getTotpEnabled, false)
                .eq(UserInfo::getTotpSecret, secret)
                .set(UserInfo::getTotpEnabled, true)
                .set(UserInfo::getTotpLastStep, step)
                .set(UserInfo::getTotpRecoveryCodes, String.join(",", hashes)));
        if (updated != 1) {
            throw new ConflictException("二维码已更新，请用最新的二维码重新绑定");
        }
        failures.invalidate(user.getId());
        log.info("用户启用两步验证 - userId: {}", user.getId());

        user.setTotpEnabled(true).setTotpLastStep(step).setTotpRecoveryCodes(String.join(",", hashes));
        return new EnableResult(user, codes);
    }

    public record EnableResult(UserInfo user, List<String> recoveryCodes) {
    }

    private UserInfo resolve(String mfaToken) {
        JwtUtil.ParsedToken parsed = jwtUtil.parseToken(mfaToken);
        if (parsed == null || parsed.tokenType() != JwtTokenType.MFA) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "验证已超时，请重新登录");
        }
        UserInfo user;
        try {
            user = userInfoMapper.selectById(Long.parseLong(parsed.subject()));
        } catch (NumberFormatException e) {
            user = null;
        }
        if (user == null || Boolean.FALSE.equals(user.getIsEnabled()) || UserBans.isBanned(user)
                || !jwtUtil.matchesCredentials(parsed, user)) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "验证已失效，请重新登录");
        }
        return user;
    }

    private void requireSetupAllowed(UserInfo user) {
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new ConflictException("两步验证已启用，请重新登录");
        }
        if (!userRoleService.isSuperAdmin(user.getId())) {
            throw new ForbiddenException("只有超级管理员需要绑定验证器");
        }
    }

    private boolean acceptTotp(UserInfo user, String code) {
        long step = Totp.matchStep(user.getTotpSecret(), code, Totp.currentStep(), user.getTotpLastStep());
        if (step < 0) {
            return false;
        }
        // 条件更新：并发提交同一个码只有一个能成功
        return userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getId, user.getId())
                .and(w -> w.isNull(UserInfo::getTotpLastStep).or().lt(UserInfo::getTotpLastStep, step))
                .set(UserInfo::getTotpLastStep, step)) == 1;
    }

    private boolean acceptRecoveryCode(UserInfo user, String code) {
        String stored = user.getTotpRecoveryCodes();
        if (code.isEmpty() || stored == null || stored.isEmpty()) {
            return false;
        }
        String hash = sha256(code);
        List<String> remaining = new ArrayList<>(Arrays.asList(stored.split(",")));
        if (!remaining.remove(hash)) {
            return false;
        }
        boolean ok = userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getId, user.getId())
                .eq(UserInfo::getTotpRecoveryCodes, stored)
                .set(UserInfo::getTotpRecoveryCodes, String.join(",", remaining))) == 1;
        if (ok) {
            log.warn("用户使用恢复码登录 - userId: {}，剩余 {} 个", user.getId(), remaining.size());
        }
        return ok;
    }

    private void checkNotLocked(Long userId) {
        AtomicInteger count = failures.getIfPresent(userId);
        if (count != null && count.get() >= MAX_FAILURES) {
            throw new BadRequestException(ErrorCode.CODE_TOO_MANY_TRIES.getCode(), "验证码错误次数过多，请 15 分钟后再试");
        }
    }

    private RuntimeException fail(Long userId) {
        int count = failures.asMap().computeIfAbsent(userId, k -> new AtomicInteger()).incrementAndGet();
        log.warn("两步验证失败 - userId: {}，15 分钟内第 {} 次", userId, count);
        return new BadRequestException("验证码错误");
    }

    private static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase();
    }

    private static String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

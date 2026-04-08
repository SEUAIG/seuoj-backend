package com.seuoj.seuojbackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.dto.auth.SendCodeDTO;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.vo.auth.SendCodeVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务
 */
@Slf4j
@Service
public class VerificationCodeService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    /**
     * 验证码有效期（秒）
     */
    @Value("${verification.code-expire-seconds:300}")
    private int codeExpireSeconds;

    /**
     * 发送间隔（秒）
     */
    @Value("${verification.send-interval-seconds:60}")
    private int sendIntervalSeconds;

    /**
     * 缓存最大容量，防止内存溢出
     */
    @Value("${verification.cache-max-size:10000}")
    private int cacheMaxSize;

    /**
     * 单次验证码允许失败次数
     */
    @Value("${verification.max-verify-fails:5}")
    private int maxVerifyFails;

    /**
     * 开发临时模式：固定验证码并跳过邮件发送
     */
    @Value("${verification.dev-fixed-code-enabled:false}")
    private boolean devFixedCodeEnabled;

    @Value("${verification.dev-fixed-code:123456}")
    private String devFixedCode;

    /**
     * 存储验证码：verificationId -> code
     * 写入后 CODE_EXPIRE_SECONDS 秒自动过期
     */
    private Cache<String, String> codeStore;

    /**
     * 存储 verificationId -> email 的映射
     * 与验证码同时过期
     */
    private Cache<String, String> verificationIdToEmail;

    /**
     * 存储验证码预留状态：verificationId -> reserved
     * 与验证码同时过期
     */
    private Cache<String, Boolean> reservedStore;

    /**
     * 存储邮箱最后发送时间：email -> timestamp
     * 写入后 SEND_INTERVAL_SECONDS 秒自动过期
     */
    private Cache<String, Long> emailLastSendTime;

    /**
     * 存储验证码失败次数：verificationId -> failCount
     * 与验证码同时过期
     */
    private Cache<String, Integer> verifyFailCount;

    public VerificationCodeService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    private void initCaches() {
        // 初始化验证码存储缓存
        this.codeStore = Caffeine.newBuilder()
                .expireAfterWrite(codeExpireSeconds, TimeUnit.SECONDS)
                .maximumSize(cacheMaxSize)
                .build();

        // 初始化 verificationId -> email 映射缓存
        this.verificationIdToEmail = Caffeine.newBuilder()
                .expireAfterWrite(codeExpireSeconds, TimeUnit.SECONDS)
                .maximumSize(cacheMaxSize)
                .build();

        // 初始化验证码预留状态缓存
        this.reservedStore = Caffeine.newBuilder()
                .expireAfterWrite(codeExpireSeconds, TimeUnit.SECONDS)
                .maximumSize(cacheMaxSize)
                .build();

        // 初始化邮箱发送频率限制缓存
        this.emailLastSendTime = Caffeine.newBuilder()
                .expireAfterWrite(sendIntervalSeconds, TimeUnit.SECONDS)
                .maximumSize(cacheMaxSize)
                .build();

        // 初始化验证码失败次数缓存
        this.verifyFailCount = Caffeine.newBuilder()
                .expireAfterWrite(codeExpireSeconds, TimeUnit.SECONDS)
                .maximumSize(cacheMaxSize)
                .build();
    }

    /**
     * 发送验证码
     */
    public SendCodeVO sendCode(SendCodeDTO dto) {
        String email = normalizeEmail(dto.getEmail());

        // 检查发送频率
        Long lastSendTime = emailLastSendTime.getIfPresent(email);
        if (lastSendTime != null) {
            long elapsed = (System.currentTimeMillis() - lastSendTime) / 1000;
            int nextSendIn = (int) (sendIntervalSeconds - elapsed);
            if (nextSendIn > 0) {
                throw new BadRequestException(ErrorCode.CODE_SEND_TOO_FREQUENT.getCode(),
                        "发送过于频繁，请" + nextSendIn + " 秒后再试");
            }
        }

        // 生成验证码（开发临时模式下使用固定值）
        String code = resolveCode();

        // 生成验证码会话ID
        String verificationId = UUID.randomUUID().toString();

        // 生产模式发送邮件，开发临时模式仅写缓存不发邮件
        if (devFixedCodeEnabled) {
            log.info("开发临时验证码模式已启用，跳过邮件发送: {}, verificationId: {}", email, verificationId);
        } else {
            sendEmail(email, code);
        }

        codeStore.put(verificationId, code);
        verificationIdToEmail.put(verificationId, email);
        emailLastSendTime.put(email, System.currentTimeMillis());
        verifyFailCount.invalidate(verificationId);

        log.info("验证码已发送到邮箱: {}, verificationId: {}", email, verificationId);

        // 构建响应
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(codeExpireSeconds);
        vo.setNextSendIn(sendIntervalSeconds);
        vo.setVerificationId(verificationId);

        return vo;
    }

    /**
     * 验证验证码（单次使用，原子消费）
     *
     * @param verificationId 验证会话ID
     * @param code           用户输入的验证码
     * @return 验证通过返回对应的邮箱，否则返回null
     */
    public String verifyCode(String verificationId, String code) {
        String storedCode = codeStore.getIfPresent(verificationId);
        if (storedCode == null) {
            return null;
        }

        if (reservedStore.getIfPresent(verificationId) != null) {
            return null;
        }

        Integer failCount = verifyFailCount.getIfPresent(verificationId);
        if (failCount != null && failCount >= maxVerifyFails) {
            invalidateVerification(verificationId);
            log.warn("验证码失败次数超限，已作废 - verificationId: {}", verificationId);
            throw new BadRequestException(ErrorCode.CODE_TOO_MANY_TRIES.getCode(), "验证码尝试次数过多，请重新获取");
        }

        if (storedCode.equals(code)) {
            String email = verificationIdToEmail.getIfPresent(verificationId);
            // 验证成功后删除验证码（一次性使用）
            invalidateVerification(verificationId);
            verifyFailCount.invalidate(verificationId);
            return email;
        }

        int nextFailCount = failCount == null ? 1 : failCount + 1;
        verifyFailCount.put(verificationId, nextFailCount);
        if (nextFailCount >= maxVerifyFails) {
            invalidateVerification(verificationId);
            log.warn("验证码失败次数超限，已作废 - verificationId: {}", verificationId);
            throw new BadRequestException(ErrorCode.CODE_TOO_MANY_TRIES.getCode(), "验证码尝试次数过多，请重新获取");
        }
        return null;
    }

    /**
     * 预校验验证码并进行预留，避免注册失败时验证码被提前消费
     *
     * @param verificationId 验证会话ID
     * @param code           用户输入的验证码
     * @return 验证通过返回对应的邮箱，否则返回null
     */
    public String preVerifyCode(String verificationId, String code) {
        // 首先尝试获取验证码条目
        String storedCode = codeStore.getIfPresent(verificationId);
        if (storedCode == null) {
            return null;
        }

        if (reservedStore.getIfPresent(verificationId) != null) {
            return null;
        }

        // 检查验证码失败次数，避免暴力破解
        Integer failCount = verifyFailCount.getIfPresent(verificationId);
        if (failCount != null && failCount >= maxVerifyFails) {
            invalidateVerification(verificationId);
            log.warn("验证码失败次数超限，已作废 - verificationId: {}", verificationId);
            throw new BadRequestException(ErrorCode.CODE_TOO_MANY_TRIES.getCode(), "验证码尝试次数过多，请重新获取");
        }

        // 尝试匹配验证码
        if (storedCode.equals(code)) {
            Boolean existing = reservedStore.asMap().putIfAbsent(verificationId, Boolean.TRUE);
            if (existing != null) {
                return null;
            }

            String latestCode = codeStore.getIfPresent(verificationId);
            if (latestCode == null || !latestCode.equals(code)) {
                releaseReservation(verificationId);
                return null;
            }

            String email = verificationIdToEmail.getIfPresent(verificationId);
            if (email == null) {
                // email 映射丢失时避免预留卡死
                invalidateVerification(verificationId);
                return null;
            }
            return email;
        }

        // 验证码匹配失败，更新失败次数，并再次校验
        int nextFailCount = failCount == null ? 1 : failCount + 1;
        verifyFailCount.put(verificationId, nextFailCount);
        if (nextFailCount >= maxVerifyFails) {
            invalidateVerification(verificationId);
            log.warn("验证码失败次数超限，已作废 - verificationId: {}", verificationId);
            throw new BadRequestException(ErrorCode.CODE_TOO_MANY_TRIES.getCode(), "验证码尝试次数过多，请重新获取");
        }
        return null;
    }

    /**
     * 注册成功后消费验证码
     */
    public void consumeVerification(String verificationId) {
        invalidateVerification(verificationId);
        verifyFailCount.invalidate(verificationId);
    }

    /**
     * 注册失败时释放预留
     */
    public void releaseReservation(String verificationId) {
        reservedStore.invalidate(verificationId);
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private String resolveCode() {
        if (!devFixedCodeEnabled) {
            return generateCode();
        }
        return (devFixedCode == null || devFixedCode.trim().isEmpty()) ? "123456" : devFixedCode.trim();
    }

    /**
     * 发送验证码邮件
     */
    private void sendEmail(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("【SEUOJ】注册验证码");
            message.setText("您的注册验证码是：" + code + "，有效期" + (codeExpireSeconds / 60) + "分钟。如非本人操作，请忽略此邮件。");
            mailSender.send(message);
            log.info("验证码邮件发送成功: {}", to);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: {}", to, e);
            throw new BadRequestException("邮件发送失败，请稍后重试");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private void invalidateVerification(String verificationId) {
        codeStore.invalidate(verificationId);
        verificationIdToEmail.invalidate(verificationId);
        reservedStore.invalidate(verificationId);
    }
}

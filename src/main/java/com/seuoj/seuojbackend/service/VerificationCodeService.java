package com.seuoj.seuojbackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.seuoj.seuojbackend.dto.auth.SendCodeDTO;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.vo.auth.SendCodeVO;
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
    private static final int CODE_EXPIRE_SECONDS = 300; // 5分钟

    /**
     * 发送间隔（秒）
     */
    private static final int SEND_INTERVAL_SECONDS = 60; // 1分钟

    /**
     * 缓存最大容量，防止内存溢出
     */
    private static final int MAX_CACHE_SIZE = 10000;

    /**
     * 存储验证码：verificationId -> code
     * 写入后 CODE_EXPIRE_SECONDS 秒自动过期
     */
    private final Cache<String, String> codeStore;

    /**
     * 存储 verificationId 与 email 的映射
     * 与验证码同时过期
     */
    private final Cache<String, String> verificationIdToEmail;

    /**
     * 存储邮箱最后发送时间：email -> timestamp
     * 写入后 SEND_INTERVAL_SECONDS 秒自动过期
     */
    private final Cache<String, Long> emailLastSendTime;

    public VerificationCodeService(JavaMailSender mailSender) {
        this.mailSender = mailSender;

        // 初始化验证码存储缓存
        this.codeStore = Caffeine.newBuilder()
                .expireAfterWrite(CODE_EXPIRE_SECONDS, TimeUnit.SECONDS)
                .maximumSize(MAX_CACHE_SIZE)
                .build();

        // 初始化 verificationId -> email 映射缓存
        this.verificationIdToEmail = Caffeine.newBuilder()
                .expireAfterWrite(CODE_EXPIRE_SECONDS, TimeUnit.SECONDS)
                .maximumSize(MAX_CACHE_SIZE)
                .build();

        // 初始化邮箱发送频率限制缓存
        this.emailLastSendTime = Caffeine.newBuilder()
                .expireAfterWrite(SEND_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(MAX_CACHE_SIZE)
                .build();
    }

    /**
     * 发送验证码
     */
    public SendCodeVO sendCode(SendCodeDTO dto) {
        String email = dto.getEmail();

        // 检查发送频率
        Long lastSendTime = emailLastSendTime.getIfPresent(email);
        if (lastSendTime != null) {
            long elapsed = (System.currentTimeMillis() - lastSendTime) / 1000;
            int nextSendIn = (int) (SEND_INTERVAL_SECONDS - elapsed);
            if (nextSendIn > 0) {
                throw new BadRequestException("发送过于频繁，请 " + nextSendIn + " 秒后再试");
            }
        }

        // 生成6位验证码
        String code = generateCode();

        // 生成验证会话ID
        String verificationId = UUID.randomUUID().toString();

        // 先发送邮件，成功后再写入缓存
        sendEmail(email, code);

        codeStore.put(verificationId, code);
        verificationIdToEmail.put(verificationId, email);
        emailLastSendTime.put(email, System.currentTimeMillis());

        log.info("验证码已发送到邮箱: {}, verificationId: {}", email, verificationId);

        // 构建响应
        SendCodeVO vo = new SendCodeVO();
        vo.setExpireIn(CODE_EXPIRE_SECONDS);
        vo.setNextSendIn(SEND_INTERVAL_SECONDS);
        vo.setVerificationId(verificationId);

        return vo;
    }

    /**
     * 验证验证码
     *
     * @param verificationId 验证会话ID
     * @param code           用户输入的验证码
     * @return 验证通过返回对应的邮箱，否则返回null
     */
    public String verifyCode(String verificationId, String code) {
        // getIfPresent 返回 null 表示已过期或不存在
        String storedCode = codeStore.getIfPresent(verificationId);
        if (storedCode == null) {
            return null;
        }

        // 验证码匹配
        if (storedCode.equals(code)) {
            String email = verificationIdToEmail.getIfPresent(verificationId);
            // 验证成功后删除验证码（一次性使用）
            codeStore.invalidate(verificationId);
            verificationIdToEmail.invalidate(verificationId);
            return email;
        }

        return null;
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
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
            message.setText("您的注册验证码是：" + code + "，有效期" + (CODE_EXPIRE_SECONDS / 60) + "分钟。如非本人操作，请忽略此邮件。");
            mailSender.send(message);
            log.info("验证码邮件发送成功: {}", to);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: {}", to, e);
            throw new BadRequestException("邮件发送失败，请稍后重试");
        }
    }
}

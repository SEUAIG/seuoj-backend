package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.dto.auth.SendCodeDTO;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.vo.auth.SendCodeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
     * 存储验证码信息：verificationId -> CodeInfo
     */
    private final Map<String, CodeInfo> codeStore = new ConcurrentHashMap<>();

    /**
     * 存储邮箱最后发送时间：email -> timestamp
     */
    private final Map<String, Long> emailLastSendTime = new ConcurrentHashMap<>();

    /**
     * 存储 verificationId 与 email 的映射
     */
    private final Map<String, String> verificationIdToEmail = new ConcurrentHashMap<>();

    public VerificationCodeService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送验证码
     */
    public SendCodeVO sendCode(SendCodeDTO dto) {
        String email = dto.getEmail();
        long now = System.currentTimeMillis();

        // 检查发送频率
        Long lastSendTime = emailLastSendTime.get(email);
        if (lastSendTime != null) {
            long elapsed = (now - lastSendTime) / 1000;
            if (elapsed < SEND_INTERVAL_SECONDS) {
                int nextSendIn = (int) (SEND_INTERVAL_SECONDS - elapsed);
                throw new BadRequestException("发送过于频繁，请 " + nextSendIn + " 秒后再试");
            }
        }

        // 生成6位验证码
        String code = generateCode();

        // 生成验证会话ID
        String verificationId = UUID.randomUUID().toString();

        // 存储验证码信息
        CodeInfo codeInfo = new CodeInfo(code, now + CODE_EXPIRE_SECONDS * 1000L);
        codeStore.put(verificationId, codeInfo);
        verificationIdToEmail.put(verificationId, email);
        emailLastSendTime.put(email, now);

        // 发送邮件
        sendEmail(email, code);

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
        CodeInfo codeInfo = codeStore.get(verificationId);
        if (codeInfo == null) {
            return null;
        }

        // 检查是否过期
        if (System.currentTimeMillis() > codeInfo.expireTime) {
            codeStore.remove(verificationId);
            verificationIdToEmail.remove(verificationId);
            return null;
        }

        // 验证码匹配
        if (codeInfo.code.equals(code)) {
            String email = verificationIdToEmail.get(verificationId);
            // 验证成功后删除验证码（一次性使用）
            codeStore.remove(verificationId);
            verificationIdToEmail.remove(verificationId);
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

    /**
     * 验证码信息
     */
    private record CodeInfo(String code, long expireTime) {
    }
}

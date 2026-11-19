package com.seuoj.seuojbackend.util;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP：HMAC-SHA1、30 秒一步、6 位数字，与 Google / Microsoft Authenticator 默认参数一致。
 */
public final class Totp {
    public static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static long currentStep() {
        return Math.floorDiv(System.currentTimeMillis() / 1000, PERIOD_SECONDS);
    }

    /**
     * 在 step-1..step+1 内找与 code 匹配且大于 lastUsedStep 的时间步，找不到返回 -1。
     * 前后各放宽一步，容忍手机时钟偏差和输入耗时。
     */
    public static long matchStep(String secret, String code, long step, Long lastUsedStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        byte[] key = base32Decode(secret);
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            if (lastUsedStep != null && candidate <= lastUsedStep) {
                continue;
            }
            if (constantTimeEquals(codeAt(key, candidate), code)) {
                return candidate;
            }
        }
        return -1;
    }

    public static String codeAt(String secret, long step) {
        return codeAt(base32Decode(secret), step);
    }

    /**
     * 验证器扫码用的 otpauth 链接
     */
    public static String otpauthUri(String issuer, String account, String secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] message = new byte[8];
            for (int i = 7; i >= 0; i--) {
                message[i] = (byte) (step & 0xff);
                step >>>= 8;
            }
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("计算 TOTP 失败", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toUpperCase().toCharArray()) {
            if (c == '=' || c == ' ') {
                continue;
            }
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("非法 Base32 字符");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

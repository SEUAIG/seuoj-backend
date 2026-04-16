package com.seuoj.seuojbackend.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于 Ed25519 的 JWT 工具类。
 */
@Slf4j
@Component
public class JwtUtil {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Value("${jwt.private-key:}")
    private String configuredPrivateKey;

    @Value("${jwt.public-key:}")
    private String configuredPublicKey;

    @Value("${jwt.access-expiration:86400}")
    private long accessExpirationSeconds;

    @Value("${jwt.temp-expiration:300}")
    private long tempExpirationSeconds;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public JwtUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        boolean privateKeyConfigured = hasConfiguredValue(configuredPrivateKey);
        boolean publicKeyConfigured = hasConfiguredValue(configuredPublicKey);

        if (privateKeyConfigured != publicKeyConfigured) {
            throw new IllegalStateException("jwt.private-key 与 jwt.public-key 必须同时配置");
        }

        try {
            if (!privateKeyConfigured) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
                KeyPair keyPair = generator.generateKeyPair();
                this.privateKey = keyPair.getPrivate();
                this.publicKey = keyPair.getPublic();
                log.warn("未配置 JWT 密钥对，已为当前进程生成临时 Ed25519 密钥对");
                return;
            }

            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            byte[] privateKeyBytes = Base64.getDecoder().decode(normalizeKey(configuredPrivateKey));
            byte[] publicKeyBytes = Base64.getDecoder().decode(normalizeKey(configuredPublicKey));
            this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("初始化 JWT Ed25519 密钥失败", e);
        }
    }

    public String createAccessToken(String userPublicId) {
        return createToken(userPublicId, JwtTokenType.ACCESS, accessExpirationSeconds);
    }

    public String createTempToken(String userPublicId) {
        return createToken(userPublicId, JwtTokenType.TEMP, tempExpirationSeconds);
    }

    public ParsedToken parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] signatureBytes = URL_DECODER.decode(parts[2]);
            verifySignature(signingInput, signatureBytes);

            Map<String, Object> header = readJson(parts[0]);
            if (!"EdDSA".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) {
                return null;
            }

            Map<String, Object> payload = readJson(parts[1]);
            String subject = asString(payload.get("sub"));
            String tokenTypeValue = asString(payload.get("token_type"));
            Long expiration = asLong(payload.get("exp"));

            if (subject == null || subject.isBlank() || tokenTypeValue == null || expiration == null) {
                return null;
            }

            if (expiration <= Instant.now().getEpochSecond()) {
                return null;
            }

            JwtTokenType tokenType = JwtTokenType.fromClaimValue(tokenTypeValue);
            return new ParsedToken(subject, tokenType);
        } catch (Exception e) {
            return null;
        }
    }

    private String createToken(String userPublicId, JwtTokenType tokenType, long expirationSeconds) {
        if (userPublicId == null || userPublicId.isBlank()) {
            throw new IllegalArgumentException("userPublicId 不能为空");
        }

        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "EdDSA");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userPublicId);
        payload.put("iat", now);
        payload.put("exp", now + expirationSeconds);
        payload.put("token_type", tokenType.getClaimValue());

        try {
            String encodedHeader = encodeJson(header);
            String encodedPayload = encodeJson(payload);
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = URL_ENCODER.encodeToString(sign(signingInput));
            return signingInput + "." + signature;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 JWT 负载失败", e);
        }
    }

    private String encodeJson(Map<String, Object> value) throws JsonProcessingException {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private Map<String, Object> readJson(String encodedPart) throws IOException {
        byte[] bytes = URL_DECODER.decode(encodedPart);
        return objectMapper.readValue(bytes, MAP_TYPE);
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private void verifySignature(String signingInput, byte[] signatureBytes) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(publicKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        if (!signature.verify(signatureBytes)) {
            throw new GeneralSecurityException("JWT 签名无效");
        }
    }

    private boolean hasConfiguredValue(String key) {
        return key != null && !key.isBlank() && !isPlaceholderValue(key);
    }

    private boolean isPlaceholderValue(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("<") && trimmed.endsWith(">");
    }

    private String normalizeKey(String value) {
        return value
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public record ParsedToken(String subject, JwtTokenType tokenType) {
    }
}

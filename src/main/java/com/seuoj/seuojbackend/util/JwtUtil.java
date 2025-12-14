package com.seuoj.seuojbackend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration; // seconds

    /**
     * 创建 JWT token，包含用户UUID
     * @param userId 用户UUID字符串
     * @return 已签名的JWT字符串
     */
    public String createToken(String userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + (expiration * 1000));

        return Jwts.builder()
                .setSubject("auth")
                .setIssuedAt(now)
                .setExpiration(exp)
                .claim("userId", userId)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /**
     * 从JWT中解析出用户UUID
     * @param token JWT字符串
     * @return 用户UUID，如果解析失败或过期返回null
     */
    public String parseUserId(String token) {
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token);
            Object val = jws.getBody().get("userId");
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            // 包括过期、签名不匹配等异常，统一返回null
            return null;
        }
    }

    /**
     * 验证Token是否有效（签名正确且未过期）
     * @param token JWT字符串
     * @return true有效，false无效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}


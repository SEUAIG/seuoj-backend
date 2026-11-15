package com.seuoj.seuojbackend.unit.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.interceptor.JwtAuthInterceptor;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.util.JwtTokenType;
import com.seuoj.seuojbackend.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 改密码后旧令牌必须作废：管理员重设、邮箱找回、自己修改三条路径都会换掉密码哈希。
 */
class PasswordChangeTokenTest {
    final JwtUtil jwt = new JwtUtil(new ObjectMapper());
    final UserInfoMapper users = mock(UserInfoMapper.class);
    final JwtAuthInterceptor interceptor = new JwtAuthInterceptor(jwt, users);
    UserInfo user;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(jwt, "accessExpirationSeconds", 3600L);
        ReflectionTestUtils.setField(jwt, "tempExpirationSeconds", 300L);
        jwt.init();
        user = new UserInfo().setId(7L).setPassword("$2a$10$oldhash").setIsEnabled(true);
        when(users.selectById(7L)).thenAnswer(inv -> user);
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    @Test
    void tokenStaysValidWhilePasswordUnchanged() throws Exception {
        String token = jwt.createAccessToken(user);
        assertTrue(interceptor.preHandle(request(token), new MockHttpServletResponse(), handler()));
    }

    @Test
    void tokenRejectedAfterPasswordChanged() throws Exception {
        String token = jwt.createAccessToken(user);
        user.setPassword("$2a$10$newhash");
        AuthorizationException e = assertThrows(AuthorizationException.class,
                () -> interceptor.preHandle(request(token), new MockHttpServletResponse(), handler()));
        assertEquals(40100, e.getCode());
    }

    @Test
    void tokenIssuedAfterChangeIsValid() throws Exception {
        jwt.createAccessToken(user);
        user.setPassword("$2a$10$newhash");
        String fresh = jwt.createAccessToken(user);
        assertTrue(interceptor.preHandle(request(fresh), new MockHttpServletResponse(), handler()));
    }

    @Test
    void tempTokenAlsoCarriesStamp() {
        JwtUtil.ParsedToken parsed = jwt.parseToken(jwt.createTempToken(user));
        assertEquals(JwtTokenType.TEMP, parsed.tokenType());
        assertTrue(jwt.matchesCredentials(parsed, user));
        user.setPassword("$2a$10$newhash");
        assertFalse(jwt.matchesCredentials(parsed, user));
    }

    @Test
    void legacyTokenWithoutStampRejected() {
        assertFalse(jwt.matchesCredentials(new JwtUtil.ParsedToken("7", JwtTokenType.ACCESS, null), user));
    }

    @Test
    void stampDoesNotExposePasswordHash() {
        String stamp = jwt.parseToken(jwt.createAccessToken(user)).credentialStamp();
        assertNotNull(stamp);
        assertFalse(stamp.contains("oldhash"));
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/me");
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    private static HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
    }
}

package com.seuoj.seuojbackend.unit.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.JwtAuthInterceptor;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.service.MfaService;
import com.seuoj.seuojbackend.service.UserRoleService;
import com.seuoj.seuojbackend.util.JwtUtil;
import com.seuoj.seuojbackend.util.Totp;
import com.seuoj.seuojbackend.vo.auth.LoginVO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 超级管理员两步验证：TOTP 算法、登录分段、令牌失效、防重放、恢复码、错误次数上限。
 */
class MfaTest {
    final JwtUtil jwt = new JwtUtil(new ObjectMapper());
    final UserInfoMapper users = mock(UserInfoMapper.class);
    final UserRoleService roles = mock(UserRoleService.class);
    final MfaService mfa = new MfaService(users, roles, jwt);
    UserInfo user;

    @BeforeAll
    static void initTableInfo() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), "mfa");
        assistant.setCurrentNamespace("mfa");
        TableInfoHelper.initTableInfo(assistant, UserInfo.class);
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(jwt, "accessExpirationSeconds", 3600L);
        ReflectionTestUtils.setField(jwt, "tempExpirationSeconds", 300L);
        jwt.init();
        user = new UserInfo().setId(1L).setUsername("superadmin").setPassword("$2a$10$hash")
                .setIsEnabled(true).setTotpEnabled(false);
        when(users.selectById(1L)).thenAnswer(inv -> user);
        when(roles.isSuperAdmin(1L)).thenReturn(true);
        when(users.update(isNull(), any())).thenReturn(1);
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    @Test
    void rfc6238Vectors() {
        // RFC 6238 附录 B 的 SHA1 用例，取低 6 位
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // "12345678901234567890"
        assertEquals("287082", Totp.codeAt(secret, 59 / 30));
        assertEquals("081804", Totp.codeAt(secret, 1111111109L / 30));
        assertEquals("005924", Totp.codeAt(secret, 1234567890L / 30));
        assertEquals("279037", Totp.codeAt(secret, 2000000000L / 30));
    }

    @Test
    void matchStepToleratesOneStepAndRejectsReplay() {
        String secret = Totp.generateSecret();
        long now = 1000;
        assertEquals(999, Totp.matchStep(secret, Totp.codeAt(secret, 999), now, null));
        assertEquals(1001, Totp.matchStep(secret, Totp.codeAt(secret, 1001), now, null));
        assertEquals(-1, Totp.matchStep(secret, Totp.codeAt(secret, 998), now, null));
        assertEquals(-1, Totp.matchStep(secret, Totp.codeAt(secret, 1000), now, 1000L));
        assertEquals(-1, Totp.matchStep(secret, "12345", now, null));
    }

    @Test
    void ordinaryUserLogsInDirectly() {
        when(roles.isSuperAdmin(1L)).thenReturn(false);
        assertNull(mfa.challengeIfNeeded(user));
    }

    @Test
    void superAdminWithoutTotpMustSetUp() {
        LoginVO vo = mfa.challengeIfNeeded(user);
        assertEquals(MfaService.STEP_SETUP, vo.getMfa());
        assertNull(vo.getJwt());
        assertNotNull(vo.getMfaToken());
    }

    @Test
    void mfaTokenIsNotAnAccessToken() {
        String token = mfa.challengeIfNeeded(user).getMfaToken();
        var interceptor = new JwtAuthInterceptor(jwt, users);
        assertThrows(AuthorizationException.class,
                () -> interceptor.preHandle(request(token), new MockHttpServletResponse(), handler()));
    }

    @Test
    void enableFlowIssuesRecoveryCodesAndKillsOldTokens() {
        String oldAccess = jwt.createAccessToken(user);
        String token = mfa.challengeIfNeeded(user).getMfaToken();

        String secret = mfa.setup(token).getSecret();
        user.setTotpSecret(secret);
        assertThrows(BadRequestException.class, () -> mfa.enable(token, "000000".equals(current(secret)) ? "111111" : "000000"));

        MfaService.EnableResult result = mfa.enable(token, current(secret));
        List<String> codes = result.recoveryCodes();
        assertEquals(8, codes.size());
        assertTrue(codes.get(0).matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertTrue(user.getTotpEnabled());

        // 启用前签发的访问令牌和绑定用的 MFA 令牌都失效
        assertFalse(jwt.matchesCredentials(jwt.parseToken(oldAccess), user));
        assertThrows(AuthorizationException.class, () -> mfa.setup(token));
        assertTrue(jwt.matchesCredentials(jwt.parseToken(jwt.createAccessToken(user)), user));
    }

    @Test
    void setupOnlyForSuperAdmin() {
        String token = jwt.createMfaToken(user);
        when(roles.isSuperAdmin(1L)).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> mfa.setup(token));
    }

    @Test
    void verifyAcceptsCurrentCodeOnce() {
        String secret = enableDirectly();
        String token = mfa.challengeIfNeeded(user).getMfaToken();
        assertEquals(MfaService.STEP_VERIFY, mfa.challengeIfNeeded(user).getMfa());

        assertSame(user, mfa.verify(token, current(secret)));
        // 数据库已记下这一步，同一个码再交一次要被拒
        user.setTotpLastStep(Totp.currentStep() + 1);
        assertThrows(BadRequestException.class, () -> mfa.verify(token, current(secret)));
    }

    @Test
    void concurrentReuseLosesConditionalUpdate() {
        String secret = enableDirectly();
        String token = jwt.createMfaToken(user);
        when(users.update(isNull(), any())).thenReturn(0);
        assertThrows(BadRequestException.class, () -> mfa.verify(token, current(secret)));
    }

    @Test
    void recoveryCodeWorksWithoutDashOrCase() {
        enableDirectly();
        user.setTotpRecoveryCodes(sha256("ABCDEFGH") + "," + sha256("ZZZZZZZZ"));
        String token = jwt.createMfaToken(user);
        assertSame(user, mfa.verify(token, " abcd efgh "));
        assertThrows(BadRequestException.class, () -> mfa.verify(token, "WXYZ-WXYZ"));
    }

    @Test
    void tooManyFailuresLocksEvenCorrectCode() {
        String secret = enableDirectly();
        String token = jwt.createMfaToken(user);
        String wrong = "000000".equals(current(secret)) ? "111111" : "000000";
        for (int i = 0; i < 10; i++) {
            assertThrows(BadRequestException.class, () -> mfa.verify(token, wrong));
        }
        BadRequestException e = assertThrows(BadRequestException.class, () -> mfa.verify(token, current(secret)));
        assertEquals(42901, e.getCode());
    }

    @Test
    void expiredOrForeignTokenRejected() {
        enableDirectly();
        assertThrows(AuthorizationException.class, () -> mfa.verify(jwt.createAccessToken(user), "123456"));
        String token = jwt.createMfaToken(user);
        user.setPassword("$2a$10$changed");
        assertThrows(AuthorizationException.class, () -> mfa.verify(token, "123456"));
    }

    private String enableDirectly() {
        String secret = Totp.generateSecret();
        user.setTotpSecret(secret).setTotpEnabled(true);
        return secret;
    }

    private static String current(String secret) {
        return Totp.codeAt(secret, Totp.currentStep());
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    private static HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
    }
}

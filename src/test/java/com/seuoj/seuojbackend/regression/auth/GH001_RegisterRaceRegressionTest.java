package com.seuoj.seuojbackend.regression.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.auth.RegisterDTO;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import com.seuoj.seuojbackend.service.AuthService;
import com.seuoj.seuojbackend.service.VerificationCodeService;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * GH001 注册并发与验证码重试上限回归测试。
 */
class GH001_RegisterRaceRegressionTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    /**
     * 同一邮箱并发注册时，应只有一次成功，另一次触发冲突。
     */
    @Test
    void registerConcurrentSameEmailShouldOnlySucceedOnce() throws Exception {
        ensureUserRoleExists();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String email = "race_" + suffix + "@example.com";
        String code = "123456";

        RegisterDTO dto1 = buildRegister("user_a_" + suffix, email, code);
        RegisterDTO dto2 = buildRegister("user_b_" + suffix, email, code);
        putTestCode(dto1.getVerificationId(), email, code);
        putTestCode(dto2.getVerificationId(), email, code);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Throwable> others = new ArrayList<>();

        pool.submit(() -> runRegister(dto1, start, done, success, conflict, others));
        pool.submit(() -> runRegister(dto2, start, done, success, conflict, others));
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        Assertions.assertEquals(1, success.get());
        Assertions.assertEquals(1, conflict.get());
        Assertions.assertTrue(others.isEmpty(), "unexpected error: " + others);
    }

    /**
     * 验证码连续输错达到上限后，应返回固定错误码。
     */
    @Test
    void registerShouldFailAfterTooManyWrongCodes() {
        ensureUserRoleExists();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String email = "wrong_code_" + suffix + "@example.com";
        String verificationId = UUID.randomUUID().toString();
        putTestCode(verificationId, email, "123456");

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("user_fail_" + suffix);
        dto.setPassword("test1234");
        dto.setEmail(email);
        dto.setVerificationId(verificationId);
        dto.setCode("000000");

        BadRequestException last = null;
        for (int i = 0; i < 5; i++) {
            try {
                authService.register(dto);
            } catch (BadRequestException e) {
                last = e;
            }
        }

        Assertions.assertNotNull(last);
        Assertions.assertEquals(ErrorCode.CODE_TOO_MANY_TRIES.getCode(), last.getCode());
    }

    /**
     * 构造注册请求对象。
     */
    private RegisterDTO buildRegister(String username, String email, String code) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword("test1234");
        dto.setEmail(email);
        dto.setVerificationId(UUID.randomUUID().toString());
        dto.setCode(code);
        return dto;
    }

    /**
     * 并发执行单次注册，统计成功、冲突与其他异常。
     */
    private void runRegister(RegisterDTO dto, CountDownLatch start, CountDownLatch done,
                             AtomicInteger success, AtomicInteger conflict, List<Throwable> others) {
        try {
            start.await(5, TimeUnit.SECONDS);
            authService.register(dto);
            success.incrementAndGet();
        } catch (ConflictException e) {
            conflict.incrementAndGet();
        } catch (Throwable t) {
            synchronized (others) {
                others.add(t);
            }
        } finally {
            done.countDown();
        }
    }

    /**
     * 直接向验证码缓存写入测试数据，避免依赖邮件发送流程。
     */
    private void putTestCode(String verificationId, String email, String code) {
        @SuppressWarnings("unchecked")
        Cache<String, String> codeStore =
                (Cache<String, String>) ReflectionTestUtils.getField(verificationCodeService, "codeStore");
        @SuppressWarnings("unchecked")
        Cache<String, String> verificationIdToEmail =
                (Cache<String, String>) ReflectionTestUtils.getField(verificationCodeService, "verificationIdToEmail");
        if (codeStore == null || verificationIdToEmail == null) {
            throw new IllegalStateException("verification cache is unavailable");
        }
        codeStore.put(verificationId, code);
        verificationIdToEmail.put(verificationId, email);
    }

    /**
     * 确保 USER 角色在测试库中存在，避免初始化数据缺失影响回归用例。
     */
    private void ensureUserRoleExists() {
        UserRole role = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleCode, RoleType.USER.getCode())
                .eq(UserRole::getIsDel, 0));
        if (role == null) {
            userRoleMapper.insert(new UserRole()
                    .setRoleCode(RoleType.USER.getCode())
                    .setRoleName(RoleType.USER.getCode())
                    .setIsDel(0));
        }
    }
}

package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.dto.auth.RegisterDTO;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceRegisterRaceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Test
    void register_concurrent_same_email_conflicts() throws Exception {
        ensureUserRoleExists();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String email = "race_" + suffix + "@example.com";
        String password = "test1234";
        String code = "123456";

        RegisterDTO dto1 = new RegisterDTO();
        dto1.setUsername("user_a_" + suffix);
        dto1.setPassword(password);
        dto1.setEmail(email);
        dto1.setVerificationId(UUID.randomUUID().toString());
        dto1.setCode(code);

        RegisterDTO dto2 = new RegisterDTO();
        dto2.setUsername("user_b_" + suffix);
        dto2.setPassword(password);
        dto2.setEmail(email);
        dto2.setVerificationId(UUID.randomUUID().toString());
        dto2.setCode(code);

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

        log.info("并发注册结果: success={}, conflict={}, others={}", success.get(), conflict.get(), others.size());
        for (Throwable t : others) {
            log.error("并发注册出现非预期异常", t);
        }

        Assertions.assertEquals(1, success.get(), "应只有一个注册成功");
        Assertions.assertEquals(1, conflict.get(), "应有一个注册冲突");
        if (!others.isEmpty()) {
            Assertions.fail("出现非预期异常: " + others.get(0));
        }
    }

    @Test
    void register_verification_code_too_many_tries() {
        ensureUserRoleExists();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String email = "fail_" + suffix + "@example.com";
        String password = "test1234";
        String verificationId = UUID.randomUUID().toString();

        putTestCode(verificationId, email, "123456");

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("user_fail_" + suffix);
        dto.setPassword(password);
        dto.setEmail(email);
        dto.setVerificationId(verificationId);
        dto.setCode("000000");

        BadRequestException last = null;
        for (int i = 0; i < 5; i++) {
            try {
                authService.register(dto);
            } catch (BadRequestException e) {
                last = e;
                log.warn("验证码错误尝试: {}，{}", i + 1, e.getMessage());
            }
        }

        Assertions.assertNotNull(last, "应捕获验证码错误异常");
        Assertions.assertEquals(ErrorCode.CODE_TOO_MANY_TRIES.getCode(), last.getCode(), "应触发尝试次数过多");
    }

    private void runRegister(RegisterDTO dto, CountDownLatch start, CountDownLatch done,
                             AtomicInteger success, AtomicInteger conflict, List<Throwable> others) {
        try {
            start.await(5, TimeUnit.SECONDS);
            authService.register(dto);
            success.incrementAndGet();
            log.info("注册成功: username={}, email={}, verificationId={}",
                    dto.getUsername(), dto.getEmail(), dto.getVerificationId());
        } catch (ConflictException e) {
            conflict.incrementAndGet();
            log.warn("注册冲突: username={}, email={}, verificationId={}",
                    dto.getUsername(), dto.getEmail(), dto.getVerificationId());
        } catch (Throwable t) {
            synchronized (others) {
                others.add(t);
            }
            log.error("注册异常: username={}, email={}, verificationId={}",
                    dto.getUsername(), dto.getEmail(), dto.getVerificationId(), t);
        } finally {
            done.countDown();
        }
    }

    private void putTestCode(String verificationId, String email, String code) {
        @SuppressWarnings("unchecked")
        Cache<String, String> codeStore =
                (Cache<String, String>) ReflectionTestUtils.getField(verificationCodeService, "codeStore");
        @SuppressWarnings("unchecked")
        Cache<String, String> verificationIdToEmail =
                (Cache<String, String>) ReflectionTestUtils.getField(verificationCodeService, "verificationIdToEmail");
        if (codeStore == null || verificationIdToEmail == null) {
            throw new IllegalStateException("验证码缓存字段为空");
        }
        codeStore.put(verificationId, code);
        verificationIdToEmail.put(verificationId, email);
    }

    private void ensureUserRoleExists() {
        UserRole role = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleCode, RoleType.USER.getCode())
                .eq(UserRole::getIsDel, 0));
        if (role != null) {
            return;
        }
        UserRole newRole = new UserRole()
                .setRoleCode(RoleType.USER.getCode())
                .setRoleName(RoleType.USER.getCode())
                .setIsDel(0);
        userRoleMapper.insert(newRole);
    }
}

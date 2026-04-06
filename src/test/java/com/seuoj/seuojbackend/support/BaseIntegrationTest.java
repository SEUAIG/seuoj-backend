package com.seuoj.seuojbackend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 集成测试基类，统一提供 MockMvc、ObjectMapper 与 token 构造能力。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringJUnitConfig
@org.junit.jupiter.api.extension.ExtendWith(TestDbResetExtension.class)
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtUtil jwtUtil;

    /**
     * 根据用户 ID 生成带 Bearer 前缀的认证头值。
     */
    protected String bearerToken(Long userId) {
        return "Bearer " + jwtUtil.createToken(userId);
    }
}

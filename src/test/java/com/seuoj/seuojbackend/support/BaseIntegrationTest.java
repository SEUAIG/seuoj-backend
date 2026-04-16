package com.seuoj.seuojbackend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 集成测试基类，统一提供 MockMvc、ObjectMapper 与令牌构造能力。
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

    @Autowired
    protected UserInfoMapper userInfoMapper;

    protected String bearerToken(Long userId) {
        return "Bearer " + jwtUtil.createAccessToken(requiredPublicId(userId));
    }

    protected String tempBearerToken(Long userId) {
        return "Bearer " + jwtUtil.createTempToken(requiredPublicId(userId));
    }

    private String requiredPublicId(Long userId) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("未找到对应用户，userId=" + userId);
        }
        return user.getPublicId();
    }
}

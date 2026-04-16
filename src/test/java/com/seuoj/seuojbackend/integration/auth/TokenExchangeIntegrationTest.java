package com.seuoj.seuojbackend.integration.auth;

import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 令牌交换与用户信息接口集成测试。
 */
class TokenExchangeIntegrationTest extends BaseIntegrationTest {

    /**
     * 短期令牌换取长期访问令牌，应返回 access_token 且不返回 temp_token。
     */
    @Test
    void exchangeTempTokenShouldReturnAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/token/exchange")
                        .header("Authorization", tempBearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isString())
                .andExpect(jsonPath("$.data.temp_token").doesNotExist());
    }

    /**
     * 长期访问令牌换取短期令牌，应返回 temp_token 且不返回 access_token。
     */
    @Test
    void exchangeAccessTokenShouldReturnTempToken() throws Exception {
        mockMvc.perform(post("/api/auth/token/exchange")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temp_token").isString())
                .andExpect(jsonPath("$.data.access_token").doesNotExist());
    }

    /**
     * 传入非法令牌时应返回未登录错误。
     */
    @Test
    void exchangeInvalidTokenShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/token/exchange")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 使用合法访问令牌访问 /api/user/me，应返回用户基础信息。
     */
    @Test
    void userMeShouldReturnProfileWhenAccessTokenValid() throws Exception {
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").value("00000000-0000-0000-0000-000000010002"))
                .andExpect(jsonPath("$.data.username").value("normal_user"))
                .andExpect(jsonPath("$.data.email").value("user@test.local"));
    }

    /**
     * 使用短期令牌访问需要登录的用户信息接口，应返回未登录错误。
     */
    @Test
    void userMeShouldReturn401WhenTempTokenUsed() throws Exception {
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", tempBearerToken(10002L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}

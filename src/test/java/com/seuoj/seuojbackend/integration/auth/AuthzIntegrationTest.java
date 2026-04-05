package com.seuoj.seuojbackend.integration.auth;

import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限与鉴权相关集成测试。
 */
class AuthzIntegrationTest extends BaseIntegrationTest {

    /**
     * 未携带 token 访问受保护接口，应返回未登录。
     */
    @Test
    void submissionPageShouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/submission/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 携带非法 token 访问受保护接口，应返回未登录。
     */
    @Test
    void submissionPageShouldReturn401WhenTokenInvalid() throws Exception {
        mockMvc.perform(get("/api/submission/page")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 普通用户调用管理员接口，应返回权限不足。
     */
    @Test
    void createProblemShouldReturn403WhenUserRoleIsInsufficient() throws Exception {
        String requestBody = """
                {
                  "title": "role-check-problem",
                  "is_public": true,
                  "example": [{"in":"1 2","ans":"3"}]
                }
                """;

        mockMvc.perform(post("/api/problem")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}

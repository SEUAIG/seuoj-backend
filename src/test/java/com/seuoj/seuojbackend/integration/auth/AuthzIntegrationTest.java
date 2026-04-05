package com.seuoj.seuojbackend.integration.auth;

import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthzIntegrationTest extends BaseIntegrationTest {

    @Test
    void submissionPageShouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/submission/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void submissionPageShouldReturn401WhenTokenInvalid() throws Exception {
        mockMvc.perform(get("/api/submission/page")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

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

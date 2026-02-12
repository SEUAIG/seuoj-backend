package com.seuoj.seuojbackend.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProblemTestcaseUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemMapper problemMapper;

    @MockBean
    private JudgeClient judgeClient;

    @MockBean
    private UserRoleRelMapper userRoleRelMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        Problem problem = new Problem();
        problem.setPid("P1000");
        when(problemMapper.selectOne(any())).thenReturn(problem);
        when(userRoleRelMapper.getRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(jwtUtil.parseUserId(any())).thenReturn(1L);
        UserContextHolder.set(com.seuoj.seuojbackend.interceptor.UserContext.of(1L, AuthStatus.AUTHENTICATED));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ========== POST /api/problem/testcases/{pid} ==========

    @Test
    void uploadShouldReturnXAccelRedirectHeader() throws Exception {
        mockMvc.perform(post("/api/problem/testcases/{pid}", "P1000")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect",
                        "/internal/judgend/judge/problem/testcases/P1000"));
    }

    @Test
    void uploadNonExistentProblemShouldReturn404() throws Exception {
        when(problemMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(post("/api/problem/testcases/{pid}", "P9999")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isNotFound());
    }

    // ========== GET /api/problem/config/{pid} ==========

    @Test
    void configMetaShouldReturnXAccelRedirectHeader() throws Exception {
        mockMvc.perform(get("/api/problem/config/{pid}", "P1000")
                        .param("type", "META")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect",
                        "/internal/judgend/judge/problem/config/P1000?type=META"));
    }

    @Test
    void configCaseShouldReturnXAccelRedirectHeader() throws Exception {
        mockMvc.perform(get("/api/problem/config/{pid}", "P1000")
                        .param("type", "CASE")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect",
                        "/internal/judgend/judge/problem/config/P1000?type=CASE"));
    }

    @Test
    void configInvalidTypeShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/problem/config/{pid}", "P1000")
                        .param("type", "INVALID")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isBadRequest());
    }

    // ========== PUT /api/problem/config/{pid} ==========

    @Test
    void updateConfigShouldReturnXAccelRedirectHeader() throws Exception {
        mockMvc.perform(put("/api/problem/config/{pid}", "P1000")
                        .param("type", "META")
                        .contentType("text/plain")
                        .content("[time_limit]\ndefault = 1000")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect",
                        "/internal/judgend/judge/problem/config/P1000?type=META"));
    }

    @Test
    void updateConfigNonExistentProblemShouldReturn404() throws Exception {
        when(problemMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(put("/api/problem/config/{pid}", "P9999")
                        .param("type", "CASE")
                        .contentType("text/plain")
                        .content("some toml content")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isNotFound());
    }

    // ========== GET /api/problem/tree/{pid} ==========

    @Test
    void treeShouldReturnXAccelRedirectHeader() throws Exception {
        mockMvc.perform(get("/api/problem/tree/{pid}", "P1000")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect",
                        "/internal/judgend/judge/problem/tree/P1000"));
    }

    @Test
    void treeNonExistentProblemShouldReturn404() throws Exception {
        when(problemMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(get("/api/problem/tree/{pid}", "P9999")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isNotFound());
    }
}

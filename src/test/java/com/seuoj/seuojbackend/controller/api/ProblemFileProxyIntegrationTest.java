package com.seuoj.seuojbackend.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import java.nio.charset.StandardCharsets;
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
class ProblemFileProxyIntegrationTest {

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
        when(judgeClient.fetchProblemFile("P1000", "1.in")).thenReturn("1 2\n".getBytes(StandardCharsets.UTF_8));
        when(judgeClient.fetchProblemFile("P1000", "subtask1/1.in"))
                .thenReturn("3 4\n".getBytes(StandardCharsets.UTF_8));
        when(userRoleRelMapper.getRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(jwtUtil.parseUserId(any())).thenReturn(1L);
        UserContextHolder.set(com.seuoj.seuojbackend.interceptor.UserContext.of(1L, AuthStatus.AUTHENTICATED));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void fileShouldProxyFileBytes() throws Exception {
        mockMvc.perform(get("/api/problem/file/{pid}/1.in", "P1000")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"1.in\""))
                .andExpect(content().string("1 2\n"));
    }

    @Test
    void fileWithSubdirectoryShouldReturnCorrectFile() throws Exception {
        mockMvc.perform(get("/api/problem/file/{pid}/subtask1/1.in", "P1000")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"1.in\""))
                .andExpect(content().string("3 4\n"));
    }

    @Test
    void fileNonExistentProblemShouldReturn404() throws Exception {
        when(problemMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(get("/api/problem/file/{pid}/1.in", "P9999")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fileWithPathTraversalShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/problem/file/{pid}/../../../etc/passwd", "P1000")
                        .header("Authorization", "Bearer testtoken"))
                .andExpect(status().isBadRequest());
    }
}

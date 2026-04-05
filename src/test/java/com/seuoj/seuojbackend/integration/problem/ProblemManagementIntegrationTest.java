package com.seuoj.seuojbackend.integration.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemManagementIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private ProblemMapper problemMapper;

    @Test
    void adminShouldCreateProblem() throws Exception {
        String requestBody = """
                {
                  "title": "Integration Created Problem",
                  "is_public": true,
                  "example": [{"in":"1 2","ans":"3"}],
                  "description": "d",
                  "input": "i",
                  "output": "o",
                  "hint": "h"
                }
                """;

        String responseBody = mockMvc.perform(post("/api/problem")
                        .header("Authorization", bearerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String pid = objectMapper.readTree(responseBody).path("data").path("pid").asText();
        Problem created = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, pid));
        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Integration Created Problem");
    }

    @Test
    void adminShouldEditProblem() throws Exception {
        String requestBody = """
                {
                  "pid": "p-public",
                  "title": "Edited Problem Title",
                  "is_public": true
                }
                """;

        mockMvc.perform(patch("/api/problem/edit")
                        .header("Authorization", bearerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Problem edited = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "p-public"));
        assertThat(edited).isNotNull();
        assertThat(edited.getTitle()).isEqualTo("Edited Problem Title");
    }

    @Test
    void deleteShouldFailWhenProblemHasActiveRelations() throws Exception {
        mockMvc.perform(delete("/api/problem/p-public")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void deleteShouldSucceedWhenProblemHasNoRelations() throws Exception {
        Problem unlinked = new Problem()
                .setPid("p-unlinked")
                .setTitle("Unlinked")
                .setTotalSubmit(0)
                .setTotalAccept(0)
                .setIsPublic(true);
        problemMapper.insert(unlinked);

        mockMvc.perform(delete("/api/problem/p-unlinked")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Problem deleted = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "p-unlinked"));
        assertThat(deleted).isNull();
    }
}

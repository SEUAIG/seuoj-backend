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

/**
 * 题目管理主流程集成测试。
 */
class ProblemManagementIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private ProblemMapper problemMapper;

    /**
     * 管理员创建题目成功，并写入数据库。
     */
    @Test
    void adminShouldCreateProblem() throws Exception {
        String expectedPid = "TINTEGCREATE";
        String requestBody = """
                {
                  "pid": "TINTEGCREATE",
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
                .andExpect(jsonPath("$.data.pid").value(expectedPid))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String pid = objectMapper.readTree(responseBody).path("data").path("pid").asText();
        Problem created = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, pid));
        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Integration Created Problem");
    }

    /**
     * 管理员编辑题目成功，修改内容可持久化。
     */
    @Test
    void adminShouldEditProblem() throws Exception {
        String requestBody = """
                {
                  "pid": "PPUBLIC",
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

        Problem edited = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "PPUBLIC"));
        assertThat(edited).isNotNull();
        assertThat(edited.getTitle()).isEqualTo("Edited Problem Title");
    }

    /**
     * 删除仍被关联的题目，应返回冲突。
     */
    @Test
    void deleteShouldFailWhenProblemHasActiveRelations() throws Exception {
        mockMvc.perform(delete("/api/problem/p-public")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    /**
     * 删除无关联题目应成功，且数据库中不可再查到。
     */
    @Test
    void deleteShouldSucceedWhenProblemHasNoRelations() throws Exception {
        Problem unlinked = new Problem()
                .setPid("PUNLINKED")
                .setTitle("Unlinked")
                .setTotalSubmit(0)
                .setTotalAccept(0)
                .setIsPublic(true);
        problemMapper.insert(unlinked);

        mockMvc.perform(delete("/api/problem/p-unlinked")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Problem deleted = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "PUNLINKED"));
        assertThat(deleted).isNull();
    }
}

package com.seuoj.seuojbackend.regression.problem;

import com.fasterxml.jackson.databind.JsonNode;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH029 题单接口回归测试。
 */
class GH029_ProblemSetApiRegressionTest extends BaseIntegrationTest {

    @Autowired
    private ProblemSetMapper problemSetMapper;

    /**
     * 创建题单后应返回可用于后续操作的题单 ID，且题单详情应返回完整题目列表。
     */
    @Test
    void createProblemSetShouldReturnIdAndDetailShouldContainAllProblems() throws Exception {
        String createBody = """
                {
                  "title": "GH029 回归题单",
                  "description": "验证题单创建返回值和详情返回",
                  "is_public": true
                }
                """;

        String createResp = mockMvc.perform(post("/api/problem_set")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.problem_set_id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long problemSetId = objectMapper.readTree(createResp)
                .path("data")
                .path("problem_set_id")
                .asLong();
        assertThat(problemSetId).isPositive();

        ProblemSet created = problemSetMapper.selectById(problemSetId);
        assertThat(created).isNotNull();
        assertThat(created.getCreatedByUserId()).isEqualTo(10002L);
        assertThat(created.getTitle()).isEqualTo("GH029 回归题单");

        String replaceBody = """
                {
                  "problem_list": [
                    {"pid": "PPUBLIC", "order_id": 1},
                    {"pid": "PPRIVATE", "order_id": 2}
                  ]
                }
                """;
        mockMvc.perform(post("/api/problem_set/{problem_set_id}/problem", problemSetId)
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replaceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String detailResp = mockMvc.perform(get("/api/problem_set/{problem_set_id}", problemSetId)
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.problem_set_id").value(String.valueOf(problemSetId)))
                .andExpect(jsonPath("$.data.problem_list.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailData = objectMapper.readTree(detailResp).path("data");
        List<String> pids = new ArrayList<>();
        for (JsonNode item : detailData.path("problem_list")) {
            pids.add(item.path("pid").asText());
        }
        assertThat(pids).containsExactly("PPUBLIC", "PPRIVATE");
        assertThat(detailData.has("current")).isFalse();
        assertThat(detailData.has("size")).isFalse();
    }
}


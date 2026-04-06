package com.seuoj.seuojbackend.regression.problem;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.model.ProblemCommon;
import com.fasterxml.jackson.databind.JsonNode;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH030 题目可见性与鉴权回归测试。
 */
class GH030_ProblemVisibilityRegressionTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    /**
     * 统一 mock 评测端题面/配置，避免测试依赖真实 Judge 服务。
     */
    @BeforeEach
    void setUpJudgeMock() {
        when(judgeClient.fetchProblemContent(anyString())).thenReturn(mockContent());
        when(judgeClient.fetchProblemConfig(anyString())).thenReturn(mockConfig());
    }

    /**
     * 普通用户访问题库分页时，不应看到 is_public=0 的题目。
     */
    @Test
    void normalUserShouldNotSeePrivateProblemsInProblemPage() throws Exception {
        String responseBody = mockMvc.perform(get("/api/problem/page")
                        .queryParam("current", "1")
                        .queryParam("size", "20")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> pids = extractPids(responseBody);
        assertThat(pids).contains("p-public");
        assertThat(pids).doesNotContain("p-private");
    }

    /**
     * 管理员访问题库分页时，应看到公开题与私有题。
     */
    @Test
    void adminShouldSeeAllProblemsInProblemPage() throws Exception {
        String responseBody = mockMvc.perform(get("/api/problem/page")
                        .queryParam("current", "1")
                        .queryParam("size", "20")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> pids = extractPids(responseBody);
        assertThat(pids).contains("p-public", "p-private");
    }

    /**
     * 普通用户直接按 pid 查看私有题时，应返回 404 以避免信息泄露。
     */
    @Test
    void normalUserShouldReceive404WhenReadingPrivateProblemDirectly() throws Exception {
        mockMvc.perform(get("/api/problem/p-private")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /**
     * 管理员可直接查看私有题详情。
     */
    @Test
    void adminShouldReadPrivateProblemDetailDirectly() throws Exception {
        mockMvc.perform(get("/api/problem/p-private")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value("p-private"))
                .andExpect(jsonPath("$.data.is_public").value(false));
    }

    /**
     * 从分页响应中提取题目 pid 列表，便于做可见性断言。
     */
    private List<String> extractPids(String responseBody) throws Exception {
        JsonNode records = objectMapper.readTree(responseBody).path("data").path("records");
        List<String> pids = new ArrayList<>();
        for (JsonNode record : records) {
            pids.add(record.path("pid").asText());
        }
        return pids;
    }

    /**
     * 生成测试所需的题面 mock 数据。
     */
    private ProblemContentDTO mockContent() {
        ProblemContentDTO content = new ProblemContentDTO();
        content.setPid("p-private");
        content.setDescription("desc");
        content.setInput("input");
        content.setOutput("output");
        content.setHint("hint");
        ProblemCommon.Example example = new ProblemCommon.Example();
        example.setIn("1 2");
        example.setAns("3");
        content.setExample(List.of(example));
        return content;
    }

    /**
     * 生成测试所需的题目配置 mock 数据。
     */
    private ProblemConfigDTO mockConfig() {
        ProblemConfigDTO config = new ProblemConfigDTO();
        ProblemConfigDTO.ProblemInfo info = new ProblemConfigDTO.ProblemInfo();
        info.setTimeLimitMs(1000L);
        info.setMemoryLimitKb(262144L);
        info.setProblemType("Standard");
        info.setCheckerType("Standard");
        config.setProblemInfo(info);
        return config;
    }
}

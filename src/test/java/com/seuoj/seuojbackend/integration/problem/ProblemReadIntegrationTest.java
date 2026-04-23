package com.seuoj.seuojbackend.integration.problem;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.model.ProblemCommon;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 题目查看链路集成测试。
 */
class ProblemReadIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    /**
     * 每个用例前默认 mock 评测端题面与配置返回，避免外部依赖波动。
     */
    @BeforeEach
    void setUpJudgeMock() {
        when(judgeClient.fetchProblemContent(anyString())).thenReturn(mockContent());
        when(judgeClient.fetchProblemConfig(anyString())).thenReturn(mockConfig());
    }

    /**
     * 匿名用户可读取公开题目详情。
     */
    @Test
    void guestShouldReadPublicProblemDetail() throws Exception {
        mockMvc.perform(get("/api/problem/p-public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value("PPUBLIC"));
    }

    /**
     * 匿名用户读取私有题目时应返回不存在，避免信息泄露。
     */
    @Test
    void guestShouldNotReadPrivateProblemDetail() throws Exception {
        mockMvc.perform(get("/api/problem/p-private"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /**
     * 管理员可读取私有题目详情。
     */
    @Test
    void adminShouldReadPrivateProblemDetail() throws Exception {
        mockMvc.perform(get("/api/problem/p-private")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value("PPRIVATE"));
    }

    /**
     * 同时传入竞赛与题单上下文参数应被拒绝。
     */
    @Test
    void shouldRejectWhenContestAndProblemSetContextBothProvided() throws Exception {
        mockMvc.perform(get("/api/problem/p-public")
                        .queryParam("contest_id", "1")
                        .queryParam("problem_set_id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    /**
     * 评测端拉取题面失败时，应映射为网关错误。
     */
    @Test
    void shouldReturn502WhenJudgeClientFetchContentFailed() throws Exception {
        when(judgeClient.fetchProblemContent(anyString())).thenThrow(new JudgeRemoteException("judge unavailable"));

        mockMvc.perform(get("/api/problem/p-public"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(50001));
    }

    /**
     * 生成测试所需的题面 mock 数据。
     */
    private ProblemContentDTO mockContent() {
        ProblemContentDTO content = new ProblemContentDTO();
        content.setPid("PPUBLIC");
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

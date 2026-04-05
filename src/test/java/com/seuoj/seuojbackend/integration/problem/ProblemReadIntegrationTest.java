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

class ProblemReadIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @BeforeEach
    void setUpJudgeMock() {
        when(judgeClient.fetchProblemContent(anyString())).thenReturn(mockContent());
        when(judgeClient.fetchProblemConfig(anyString())).thenReturn(mockConfig());
    }

    @Test
    void guestShouldReadPublicProblemDetail() throws Exception {
        mockMvc.perform(get("/api/problem/p-public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value("p-public"));
    }

    @Test
    void guestShouldNotReadPrivateProblemDetail() throws Exception {
        mockMvc.perform(get("/api/problem/p-private"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void adminShouldReadPrivateProblemDetail() throws Exception {
        mockMvc.perform(get("/api/problem/p-private")
                        .header("Authorization", bearerToken(10001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value("p-private"));
    }

    @Test
    void shouldRejectWhenContestAndProblemSetContextBothProvided() throws Exception {
        mockMvc.perform(get("/api/problem/p-public")
                        .queryParam("contest_public_id", "c-1")
                        .queryParam("problem_set_public_id", "ps-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void shouldReturn502WhenJudgeClientFetchContentFailed() throws Exception {
        when(judgeClient.fetchProblemContent(anyString())).thenThrow(new JudgeRemoteException("judge unavailable"));

        mockMvc.perform(get("/api/problem/p-public"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(50001));
    }

    private ProblemContentDTO mockContent() {
        ProblemContentDTO content = new ProblemContentDTO();
        content.setPid("p-public");
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

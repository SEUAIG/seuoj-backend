package com.seuoj.seuojbackend.integration.judge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JudgeCallbackIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Test
    void callbackShouldRejectWhenSecretMissing() throws Exception {
        String body = """
                {
                  "status": "CompileError",
                  "errorDetail": "compile failed"
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", "s-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void callbackShouldRejectWhenSecretInvalid() throws Exception {
        String body = """
                {
                  "status": "CompileError",
                  "errorDetail": "compile failed"
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", "s-1")
                        .header("X-Judge-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void callbackShouldRejectWhenStatusUnknown() throws Exception {
        String body = """
                {
                  "status": "UnknownStatus"
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", "s-1")
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void callbackShouldReturn404WhenSubmissionNotFound() throws Exception {
        String body = """
                {
                  "status": "CompileError",
                  "errorDetail": "compile failed"
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", "not-exists")
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void callbackShouldRejectWhenSuccessWithoutResultDetail() throws Exception {
        String submissionNo = createSubmission();
        String body = """
                {
                  "status": "Success"
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", submissionNo)
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void callbackShouldRejectWhenResultDetailItemInvalid() throws Exception {
        String submissionNo = createSubmission();
        String body = """
                {
                  "status": "Success",
                  "resultDetail": [
                    {
                      "out": "3",
                      "ans": "3",
                      "time": 1,
                      "mem": 1,
                      "score": 100
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", submissionNo)
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void callbackCompileErrorShouldBeVisibleInResultQuery() throws Exception {
        String submissionNo = createSubmission();
        String body = """
                {
                  "status": "CompileError",
                  "errorDetail": "syntax error",
                  "score": 0
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", submissionNo)
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/submission/{submissionNo}", submissionNo)
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("Finished"))
                .andExpect(jsonPath("$.data.verdict").value("CompileError"))
                .andExpect(jsonPath("$.data.errorDetail").value("syntax error"));
    }

    @Test
    void callbackShouldBeIdempotentWhenRepeatedSuccess() throws Exception {
        String submissionNo = createSubmission();
        Problem before = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "p-public"));
        assertThat(before).isNotNull();
        int beforeAccept = before.getTotalAccept();
        String body = """
                {
                  "status": "Success",
                  "score": 100,
                  "resultDetail": [
                    {
                      "id": 1,
                      "in": "1 2",
                      "out": "3",
                      "ans": "3",
                      "sys": "ok",
                      "time": 5,
                      "mem": 1024,
                      "type": "Accepted",
                      "score": 100
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", submissionNo)
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/judge/submission/{submissionNo}", submissionNo)
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "p-public"));
        assertThat(problem).isNotNull();
        assertThat(problem.getTotalAccept()).isEqualTo(beforeAccept + 1);
    }

    @Test
    void callbackShouldUpdateWhenSubmissionWasFailed() throws Exception {
        String submissionNo = createSubmission();
        submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                .set(Submission::getStatus, SubmissionStatus.FAILED.getStatus())
                .eq(Submission::getSubmissionNo, submissionNo));
        String body = """
                {
                  "status": "Success",
                  "score": 100,
                  "resultDetail": [
                    {
                      "id": 1,
                      "in": "1 2",
                      "out": "3",
                      "ans": "3",
                      "sys": "ok",
                      "time": 5,
                      "mem": 1024,
                      "type": "Accepted",
                      "score": 100
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/judge/submission/{submissionNo}", submissionNo)
                        .header("X-Judge-Secret", "test-judge-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/submission/{submissionNo}", submissionNo)
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("Finished"))
                .andExpect(jsonPath("$.data.verdict").value("Accepted"))
                .andExpect(jsonPath("$.data.score").value(100));
    }

    private String createSubmission() throws Exception {
        String submitBody = """
                {
                  "pid": "p-public",
                  "language": "Java17",
                  "code": "class Main{}"
                }
                """;

        String submitResp = mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(submitResp).path("data").path("submissionNo").asText();
    }
}

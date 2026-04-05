package com.seuoj.seuojbackend.integration.submission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionExceptionIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Test
    void submitShouldReturn401WhenNoToken() throws Exception {
        String body = """
                {
                  "pid": "p-public",
                  "language": "Java17",
                  "code": "class Main{}"
                }
                """;

        mockMvc.perform(post("/api/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void getResultShouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/submission/{submissionNo}", "any-submission-no"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void submitShouldRejectWhenLanguageInvalid() throws Exception {
        String body = """
                {
                  "pid": "p-public",
                  "language": "Java8",
                  "code": "class Main{}"
                }
                """;

        mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void submitShouldRejectWhenCodeTooLong() throws Exception {
        String body = """
                {
                  "pid": "p-public",
                  "language": "Java17",
                  "code": "%s"
                }
                """.formatted("a".repeat(65536));

        mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void submitShouldRejectWhenCodeBlank() throws Exception {
        String body = """
                {
                  "pid": "p-public",
                  "language": "Java17",
                  "code": "   "
                }
                """;

        mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void submitShouldRejectWhenPidBlank() throws Exception {
        String body = """
                {
                  "pid": "  ",
                  "language": "Java17",
                  "code": "class Main{}"
                }
                """;

        mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void listSubmissionsShouldRejectWhenCurrentLessThanOne() throws Exception {
        mockMvc.perform(get("/api/submission/page")
                        .queryParam("current", "0")
                        .queryParam("size", "10")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void listSubmissionsShouldRejectWhenSizeTooLarge() throws Exception {
        mockMvc.perform(get("/api/submission/page")
                        .queryParam("current", "1")
                        .queryParam("size", "101")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void listSubmissionsShouldRejectWhenCurrentTypeInvalid() throws Exception {
        mockMvc.perform(get("/api/submission/page")
                        .queryParam("current", "invalid")
                        .queryParam("size", "10")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void getResultShouldReturn404WhenSubmissionNotExists() throws Exception {
        mockMvc.perform(get("/api/submission/{submissionNo}", "not-exists")
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void submitShouldMarkFailedWhenJudgeClientThrows() throws Exception {
        doThrow(new JudgeRemoteException("judge down")).when(judgeClient).submit(any());

        String body = """
                {
                  "pid": "p-public",
                  "language": "Java17",
                  "code": "class Main{}"
                }
                """;

        String resp = mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String submissionNo = objectMapper.readTree(resp).path("data").path("submissionNo").asText();
        Submission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<Submission>().eq(Submission::getSubmissionNo, submissionNo)
        );
        assertThat(submission).isNotNull();
        assertThat(submission.getStatus()).isEqualTo("Failed");
    }
}

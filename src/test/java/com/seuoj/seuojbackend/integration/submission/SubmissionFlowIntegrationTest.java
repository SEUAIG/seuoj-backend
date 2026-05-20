package com.seuoj.seuojbackend.integration.submission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
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

/**
 * 提交、回调、查结果主链路集成测试。
 */
class SubmissionFlowIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private ProblemMapper problemMapper;

    /**
     * 完整链路：提交成功 -> 收到评测回调 -> 查询结果与题目统计正确。
     */
    @Test
    void shouldFinishSubmissionAndQueryResult() throws Exception {
        String submitBody = """
                {
                  "pid": "PPUBLIC",
                  "language": "Java",
                  "code": "public class Main { public static void main(String[] args){ System.out.println(3); } }"
                }
                """;

        String submitResp = mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.submissionNo").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String submissionNo = objectMapper.readTree(submitResp).path("data").path("submissionNo").asText();
        Submission pending = submissionMapper.selectOne(
                new LambdaQueryWrapper<Submission>().eq(Submission::getSubmissionNo, submissionNo)
        );
        assertThat(pending).isNotNull();
        assertThat(pending.getStatus()).isEqualTo("Running");

        String callbackBody = """
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
                        .content(callbackBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/submission/{submissionNo}", submissionNo)
                        .header("Authorization", bearerToken(10002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("Finished"))
                .andExpect(jsonPath("$.data.verdict").value("Accepted"))
                .andExpect(jsonPath("$.data.score").value(100));

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "PPUBLIC"));
        assertThat(problem).isNotNull();
        assertThat(problem.getTotalSubmit()).isEqualTo(1);
        assertThat(problem.getTotalAccept()).isEqualTo(1);
    }

    /**
     * 提交私有题目应被拒绝，返回资源不存在。
     */
    @Test
    void shouldRejectSubmissionWhenProblemNotPublic() throws Exception {
        String submitBody = """
                {
                  "pid": "PPRIVATE",
                  "language": "Java",
                  "code": "class Main{}"
                }
                """;

        mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /**
     * 非所有者且非管理员查询提交结果时应返回无权限。
     */
    @Test
    void shouldRejectResultQueryWhenNotOwnerOrAdmin() throws Exception {
        String submitBody = """
                {
                  "pid": "PPUBLIC",
                  "language": "Java",
                  "code": "class Main{}"
                }
                """;

        String submitResp = mockMvc.perform(post("/api/submission")
                        .header("Authorization", bearerToken(10002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String submissionNo = objectMapper.readTree(submitResp).path("data").path("submissionNo").asText();

        mockMvc.perform(get("/api/submission/{submissionNo}", submissionNo)
                        .header("Authorization", bearerToken(10004L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}

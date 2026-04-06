package com.seuoj.seuojbackend.regression.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH033 新建题目流程与编辑题面空字段回归测试。
 */
class GH033_ProblemCreateEditRegressionTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private ProblemMapper problemMapper;

    /**
     * 管理员正常创建题目，应返回指定 pid 且完成持久化。
     */
    @Test
    void adminShouldCreateProblemWithPidSuccessfully() throws Exception {
        String pid = "p-gh033-create-1";
        String requestBody = """
                {
                  "pid": "p-gh033-create-1",
                  "title": "GH033 Create Success",
                  "is_public": true,
                  "example": [{"in":"1 2","ans":"3"}],
                  "description": "d",
                  "input": "i",
                  "output": "o",
                  "hint": "h"
                }
                """;

        mockMvc.perform(post("/api/problem")
                        .header("Authorization", bearerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value(pid));

        Problem created = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, pid));
        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("GH033 Create Success");
    }

    /**
     * 简单并发创建不同 pid 题目，两个请求都应成功且均入库。
     */
    @Test
    void concurrentCreateWithDifferentPidShouldBothSucceed() throws Exception {
        String pidA = "p-gh033-concurrent-a";
        String pidB = "p-gh033-concurrent-b";

        String bodyA = """
                {
                  "pid": "p-gh033-concurrent-a",
                  "title": "Concurrent A",
                  "is_public": true,
                  "example": [{"in":"1","ans":"1"}]
                }
                """;
        String bodyB = """
                {
                  "pid": "p-gh033-concurrent-b",
                  "title": "Concurrent B",
                  "is_public": true,
                  "example": [{"in":"2","ans":"2"}]
                }
                """;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/problem")
                                .header("Authorization", bearerToken(10001L))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyA))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            Future<Integer> second = pool.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/problem")
                                .header("Authorization", bearerToken(10001L))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyB))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        List<Problem> created = problemMapper.selectList(new LambdaQueryWrapper<Problem>()
                .in(Problem::getPid, List.of(pidA, pidB)));
        assertThat(created).hasSize(2);
    }

    /**
     * 重复 pid 创建应返回参数错误，且数据库仅保留首条记录。
     */
    @Test
    void createProblemShouldFailWhenPidAlreadyExists() throws Exception {
        String pid = "p-gh033-dup";
        String first = """
                {
                  "pid": "p-gh033-dup",
                  "title": "First Title",
                  "is_public": true,
                  "example": [{"in":"1","ans":"1"}]
                }
                """;
        String second = """
                {
                  "pid": "p-gh033-dup",
                  "title": "Second Title",
                  "is_public": false,
                  "example": [{"in":"2","ans":"2"}]
                }
                """;

        mockMvc.perform(post("/api/problem")
                        .header("Authorization", bearerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/problem")
                        .header("Authorization", bearerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(second))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pid")));

        List<Problem> rows = problemMapper.selectList(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getTitle()).isEqualTo("First Title");
    }

    /**
     * 编辑题面时仅传 pid，其他字段为 null，应成功且题目元数据保持不变。
     */
    @Test
    void editProblemShouldSucceedWhenOnlyPidProvidedAndOthersNull() throws Exception {
        String requestBody = """
                {
                  "pid": "p-public",
                  "title": null,
                  "is_public": null,
                  "tags": null,
                  "description": null,
                  "input": null,
                  "output": null,
                  "example": null,
                  "hint": null
                }
                """;

        mockMvc.perform(patch("/api/problem/edit")
                        .header("Authorization", bearerToken(10001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, "p-public"));
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Public Problem");
        assertThat(problem.getIsPublic()).isTrue();

        ArgumentCaptor<JudgeProblemEditRequest> captor = ArgumentCaptor.forClass(JudgeProblemEditRequest.class);
        verify(judgeClient, times(1)).updateProblem(captor.capture());
        JudgeProblemEditRequest request = captor.getValue();
        assertThat(request.getPid()).isEqualTo("p-public");
        assertThat(request.getDescription()).isNull();
        assertThat(request.getInput()).isNull();
        assertThat(request.getOutput()).isNull();
        assertThat(request.getExample()).isNull();
        assertThat(request.getHint()).isNull();
    }
}

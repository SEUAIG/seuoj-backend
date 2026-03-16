package com.seuoj.seuojbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "judge.server-url=http://127.0.0.1:19090",
        "storage.user-code-storage-path=./data/user-code-it"
})
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class SubmissionFlowIntegrationTest {

    private static final long TEST_USER_ID = 910001L;
    private static final long TEST_PROBLEM_ID = 910001L;
    private static final String TEST_ROLE_CODE = "USER";
    private static final int MOCK_JUDGE_PORT = 19090;

    @LocalServerPort
    private int serverPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Process mockJudgeProcess;
    private String testPid;
    private String testPublicId;
    private String testEmail;
    private String testSubmissionNo;
    private Integer existingUserRoleId;
    private boolean insertedUserRole;

    @BeforeEach
    void setUp() throws Exception {
        // 为每次测试生成独立的题目编号、用户公开 ID 和邮箱，避免和已有数据冲突。
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        testPid = "PIT" + suffix;
        testPublicId = UUID.randomUUID().toString();
        testEmail = "it_" + suffix + "@example.com";
        testSubmissionNo = null;
        insertedUserRole = false;

        // 先清理一次残留数据，再准备角色/用户/题目，并启动外部 mock judge。
        cleanDatabase();
        ensureUserRoleExists();
        insertTestData();
        startMockJudge();
    }

    @AfterEach
    void tearDown() throws Exception {
        // 按“先停服务、再删文件、最后删库数据”的顺序回收现场，保证测试可重复执行。
        stopMockJudge();
        deleteCodeFile();
        cleanDatabase();
    }

    @Test
    void shouldCompleteSubmitCallbackAndResultQueryFlow() throws Exception {
        // 使用真实 JwtUtil 生成测试用户 token，整条链路都按线上鉴权方式访问接口。
        String token = jwtUtil.createToken(TEST_USER_ID);

        String submitBody = """
                {
                  "pid": "%s",
                  "language": "java",
                  "code": "public class Main { public static void main(String[] args) { System.out.println(3); } }"
                }
                """.formatted(testPid);

        // 第一步：调用真实提交接口，后端会落 submission 表、保存代码文件，并向 mock judge 发起评测请求。
        JsonNode submitJson = sendJson(
                "POST",
                "http://127.0.0.1:" + serverPort + "/api/submission",
                submitBody,
                token
        );
        assertEquals(0, submitJson.path("code").asInt());
        testSubmissionNo = submitJson.path("data").path("submissionNo").asText();
        assertNotNull(testSubmissionNo);

        // 第二步：等待 mock judge 异步回调完成。
        // 回调成功后，submission 状态会从 Running 变成 Finished，并写入 verdict/resultDetail/score。
        JsonNode resultJson = waitForFinishedResult(token, testSubmissionNo);

        // 第三步：校验查询结果接口返回的业务字段，确认“提交 -> 回调 -> 查看结果”整条链路打通。
        assertEquals(0, resultJson.path("code").asInt());
        assertEquals(testSubmissionNo, resultJson.path("data").path("submissionNo").asText());
        assertEquals(testPid, resultJson.path("data").path("pid").asText());
        assertEquals("java", resultJson.path("data").path("language").asText());
        assertEquals("Finished", resultJson.path("data").path("status").asText());
        assertEquals("Accepted", resultJson.path("data").path("verdict").asText());
        assertEquals(100, resultJson.path("data").path("score").asInt());
        assertEquals("it_user", resultJson.path("data").path("username").asText());
        assertEquals("3", resultJson.path("data").path("resultDetail").get(0).path("out").asText());
        assertEquals("Accepted", resultJson.path("data").path("resultDetail").get(0).path("type").asText());

        // 最后直接查数据库，确认题目的提交数和通过数也被正确累计。
        Integer totalSubmit = jdbcTemplate.queryForObject(
                "SELECT total_submit FROM problem WHERE id = ?",
                Integer.class,
                TEST_PROBLEM_ID
        );
        Integer totalAccept = jdbcTemplate.queryForObject(
                "SELECT total_accept FROM problem WHERE id = ?",
                Integer.class,
                TEST_PROBLEM_ID
        );
        assertEquals(1, totalSubmit);
        assertEquals(1, totalAccept);
    }

    private JsonNode waitForFinishedResult(String token, String submissionNo) throws Exception {
        // mock judge 是异步回调，因此这里轮询查询结果接口，直到状态进入 Finished 或超时。
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        JsonNode latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = sendJson(
                    "GET",
                    "http://127.0.0.1:" + serverPort + "/api/submission/" + submissionNo,
                    null,
                    token
            );
            if ("Finished".equals(latest.path("data").path("status").asText())) {
                return latest;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("等待评测完成超时，最后一次响应: " + (latest == null ? "null" : latest.toString()));
    }

    private JsonNode sendJson(String method, String url, String body, String token) throws Exception {
        // 统一封装测试中的 HTTP 请求，避免每一步都重复拼装 header、body 和响应解析逻辑。
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), "HTTP 请求失败: " + response.body());
        return objectMapper.readTree(response.body());
    }

    private void ensureUserRoleExists() {
        // 鉴权切面会查 user_role_rel -> user_role，所以测试用户必须具备 USER 角色。
        // 如果库里本来就有 USER，则复用；如果没有，则临时插入一条并在测试后删除。
        existingUserRoleId = jdbcTemplate.query(
                "SELECT id FROM user_role WHERE role_code = ? AND is_del = 0 LIMIT 1",
                rs -> rs.next() ? rs.getInt(1) : null,
                TEST_ROLE_CODE
        );
        if (existingUserRoleId == null) {
            jdbcTemplate.update(
                    "INSERT INTO user_role (id, role_code, role_name, is_del) VALUES (?, ?, ?, 0)",
                    910001,
                    TEST_ROLE_CODE,
                    "普通用户"
            );
            existingUserRoleId = 910001;
            insertedUserRole = true;
        }
    }

    private void insertTestData() {
        // 插入一组最小可运行数据：
        // 1. user_info：用于 JWT 对应用户
        // 2. user_role_rel：让接口鉴权通过
        // 3. problem：作为提交目标题目
        jdbcTemplate.update(
                "INSERT INTO user_info (id, public_id, username, email, password, is_del) VALUES (?, ?, ?, ?, ?, 0)",
                TEST_USER_ID,
                testPublicId,
                "it_user",
                testEmail,
                "test-password"
        );
        jdbcTemplate.update(
                "INSERT INTO user_role_rel (user_id, role_id, is_del) VALUES (?, ?, 0)",
                TEST_USER_ID,
                existingUserRoleId
        );
        jdbcTemplate.update(
                "INSERT INTO problem (id, pid, title, total_submit, total_accept, is_public, is_del) VALUES (?, ?, ?, 0, 0, 1, 0)",
                TEST_PROBLEM_ID,
                testPid,
                "集成测试题目"
        );
    }

    private void cleanDatabase() {
        // 仅清理本测试自己插入的数据，不影响库里其他业务数据。
        jdbcTemplate.update("DELETE FROM submission WHERE user_id = ? OR problem_id = ?", TEST_USER_ID, TEST_PROBLEM_ID);
        jdbcTemplate.update("DELETE FROM user_role_rel WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM problem WHERE id = ?", TEST_PROBLEM_ID);
        jdbcTemplate.update("DELETE FROM user_info WHERE id = ?", TEST_USER_ID);
        if (insertedUserRole) {
            jdbcTemplate.update("DELETE FROM user_role WHERE id = ?", existingUserRoleId);
        }
    }

    private void deleteCodeFile() throws IOException {
        // 提交接口会把代码写到本地文件系统，这里把本次测试生成的代码文件一并删除。
        if (testSubmissionNo == null || testSubmissionNo.isBlank()) {
            return;
        }
        Path codePath = Paths.get("data", "user-code-it", testSubmissionNo + ".txt");
        Files.deleteIfExists(codePath);
    }

    private void startMockJudge() throws Exception {
        // 这里不是用内存 mock，而是直接拉起外部 Python 脚本。
        // 后端 submit 后会真实请求它，它再异步回调本测试启动的 Spring Boot 服务。
        ProcessBuilder builder = new ProcessBuilder("python", "tools/mock_judge.py");
        builder.directory(Paths.get(".").toAbsolutePath().normalize().toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("MOCK_JUDGE_PORT", String.valueOf(MOCK_JUDGE_PORT));
        builder.environment().put("MOCK_JUDGE_CALLBACK_BASE_URL", "http://127.0.0.1:" + serverPort);
        builder.environment().put("MOCK_JUDGE_CALLBACK_DELAY_SECONDS", "0.2");
        mockJudgeProcess = builder.start();
        startLogDrainer(mockJudgeProcess);
        waitForMockJudgeReady();
    }

    private void stopMockJudge() throws Exception {
        // 测试结束必须主动回收外部进程，否则会残留端口占用。
        if (mockJudgeProcess == null) {
            return;
        }
        mockJudgeProcess.destroy();
        if (mockJudgeProcess.isAlive()) {
            mockJudgeProcess.destroyForcibly();
        }
        mockJudgeProcess.waitFor();
        mockJudgeProcess = null;
    }

    private void startLogDrainer(Process process) {
        // 持续消费子进程输出，避免 Python 进程因输出缓冲区写满而阻塞。
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // 丢弃 mock judge 输出，避免缓冲区阻塞
                }
            } catch (IOException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void waitForMockJudgeReady() throws Exception {
        // 在真正发起提交前，先轮询 /health，确保 mock judge 已经监听端口。
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + MOCK_JUDGE_PORT + "/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("mock judge 启动超时");
    }
}

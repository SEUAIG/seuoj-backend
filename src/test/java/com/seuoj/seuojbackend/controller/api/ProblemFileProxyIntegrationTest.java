package com.seuoj.seuojbackend.controller.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProblemFileProxyIntegrationTest {

    private static MockWebServer mockWebServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private com.seuoj.seuojbackend.client.JudgeHttpClient judgeHttpClient;

    @MockBean
    private ProblemMapper problemMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (mockWebServer == null) {
            try {
                mockWebServer = new MockWebServer();
                mockWebServer.start();
            } catch (Exception ex) {
                throw new IllegalStateException("无法启动 MockWebServer", ex);
            }
        }
        registry.add("judge.server-url", () -> mockWebServer.url("/").toString());
    }

    @AfterAll
    static void tearDownServer() throws Exception {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        Problem problem = new Problem();
        problem.setPid("P1000");
        when(problemMapper.selectOne(any())).thenReturn(problem);
        ReflectionTestUtils.setField(judgeHttpClient, "judgeServerUrl", mockWebServer.url("/").toString());
    }

    @Test
    void proxyProblemFileShouldStreamFromJudgeServer() throws Exception {
        byte[] payload = "test-content".getBytes();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"1.in\"")
                .setBody(new okio.Buffer().write(payload)));

        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/problem/file/P1000/1.in", byte[].class);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment; filename=\"1.in\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(payload, response.getBody());

        var recorded = mockWebServer.takeRequest();
        org.junit.jupiter.api.Assertions.assertEquals("/judge/problem/file/P1000/1.in", recorded.getPath());
    }
}

package com.seuoj.seuojbackend.integration.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 题目包（Hydro 目录结构）导入集成测试。
 */
class ProblemImportIntegrationTest extends BaseIntegrationTest {

    private static final String PACK_PID = "TINTEGPACK";

    private static final String PROBLEM_YAML = """
            owner: 1
            pid:
            title: Pack Imported Problem
            tag:
              - 不存在的标签
            nSubmit: 0
            nAccept: 0
            """;

    private static final String PROBLEM_MARKDOWN = """
            ## 题目描述

            给定 $n$，输出任意一对正整数之和为 $n$。

            ## 输入格式

            一行一个整数 $n$。

            ## 输出格式

            一行两个正整数。

            ## 样例

            ```input1
            1 2
            ```

            ```output1
            3
            ```

            ### 样例 1 解释

            最简单的一组数据。

            ## 数据范围

            $2 \\le n \\le 10^{9}$。
            """;

    private static final String DATA_CONFIG_YAML = """
            type: default
            checker_type: testlib
            checker: checker.cpp
            time: 2s
            memory: 128m
            """;

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private ProblemMapper problemMapper;

    /**
     * 管理员导入题目包成功：题面按章节落库，且按「先传数据再写配置」的顺序调用评测端。
     */
    @Test
    void adminShouldImportProblemPack() throws Exception {
        mockMvc.perform(importRequest(defaultPackEntries(), PACK_PID, true, 10001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value(PACK_PID))
                .andExpect(jsonPath("$.data.data_file_count").value(3))
                .andExpect(jsonPath("$.data.testcase_count").value(1))
                .andExpect(jsonPath("$.data.checker_compiled").value(true));

        Problem created = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, PACK_PID));
        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Pack Imported Problem");
        assertThat(created.getIsPublic()).isTrue();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<JsonNode> configCaptor = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JudgeProblemEditRequest> statementCaptor =
                ArgumentCaptor.forClass(JudgeProblemEditRequest.class);
        InOrder judgeCalls = inOrder(judgeClient);
        judgeCalls.verify(judgeClient).updateProblem(statementCaptor.capture());
        judgeCalls.verify(judgeClient).uploadProblemData(eq(PACK_PID), dataCaptor.capture());
        judgeCalls.verify(judgeClient).updateProblemConfig(eq(PACK_PID), configCaptor.capture());
        verify(judgeClient, never()).deleteProblem(any());

        // 题面存在评测端，按 problem_zh.md 的章节拆分
        JudgeProblemEditRequest statement = statementCaptor.getValue();
        assertThat(statement.getInput()).isEqualTo("一行一个整数 $n$。");
        assertThat(statement.getOutput()).isEqualTo("一行两个正整数。");
        assertThat(statement.getHint()).contains("10^{9}");
        assertThat(statement.getExample()).hasSize(1);
        assertThat(statement.getExample().get(0).getIn()).isEqualTo("1 2");
        assertThat(statement.getExample().get(0).getAns()).isEqualTo("3");
        assertThat(statement.getExample().get(0).getDescription()).isEqualTo("最简单的一组数据。");

        // 重新打包后 testdata 目录应成为压缩包根目录，否则评测端解析不到 checker
        // testdata/config.yaml 是 Hydro 侧配置，不该出现在评测端数据目录
        assertThat(readZipEntryNames(dataCaptor.getValue()))
                .containsExactlyInAnyOrder("1.in", "1.ans", "checker.cpp");

        JsonNode config = configCaptor.getValue();
        assertThat(config.path("problem_info").path("problem_type").asText()).isEqualTo("Special");
        assertThat(config.path("problem_info").path("checker_type").asText()).isEqualTo("Special");
        assertThat(config.path("problem_info").path("time_limit_ms").asLong()).isEqualTo(2000L);
        assertThat(config.path("problem_info").path("memory_limit_kb").asLong()).isEqualTo(131072L);
        assertThat(config.path("custom_modules").path("checker_path").asText()).isEqualTo("checker.cpp");
        assertThat(config.path("testcases").get(0).path("in_path").asText()).isEqualTo("1.in");
        assertThat(config.path("testcases").get(0).path("weight").asInt()).isEqualTo(100);
    }

    /**
     * 缺少 testdata/config.yaml 时按普通题导入，并回一条警告。
     */
    @Test
    void importShouldFallBackToStandardWithoutDataConfig() throws Exception {
        Map<String, String> entries = defaultPackEntries();
        entries.remove("testdata/config.yaml");
        entries.remove("testdata/checker.cpp");
        entries.put("testdata/2.in", "2 3\n");
        entries.put("testdata/2.ans", "5\n");
        entries.put("testdata/10.in", "4 5\n");
        entries.put("testdata/10.ans", "9\n");

        mockMvc.perform(importRequest(entries, PACK_PID, false, 10001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testcase_count").value(3))
                .andExpect(jsonPath("$.data.checker_compiled").value(false))
                .andExpect(jsonPath("$.data.warnings.length()").value(1));

        ArgumentCaptor<JsonNode> configCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(judgeClient).updateProblemConfig(eq(PACK_PID), configCaptor.capture());
        JsonNode testcases = configCaptor.getValue().path("testcases");
        // 自然序：1.in < 2.in < 10.in；weight 均分且总和恰好 100
        assertThat(testcases.get(0).path("in_path").asText()).isEqualTo("1.in");
        assertThat(testcases.get(2).path("in_path").asText()).isEqualTo("10.in");
        int weightSum = 0;
        for (JsonNode testcase : testcases) {
            weightSum += testcase.path("weight").asInt();
        }
        assertThat(weightSum).isEqualTo(100);
    }

    /**
     * 写评测配置失败时，应删除评测端题目并回滚数据库。
     */
    @Test
    void importShouldRollbackWhenJudgeConfigFails() throws Exception {
        doThrow(new JudgeRemoteException("更新题目配置失败"))
                .when(judgeClient).updateProblemConfig(eq(PACK_PID), any());

        mockMvc.perform(importRequest(defaultPackEntries(), PACK_PID, true, 10001L))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(50001));

        verify(judgeClient).deleteProblem(PACK_PID);
        assertThat(problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, PACK_PID))).isNull();
    }

    /**
     * 题目包缺少 problem.yaml 时应返回 400，且不会调用评测端。
     */
    @Test
    void importShouldRejectPackWithoutManifest() throws Exception {
        Map<String, String> entries = defaultPackEntries();
        entries.remove("problem.yaml");

        mockMvc.perform(importRequest(entries, PACK_PID, true, 10001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        verify(judgeClient, never()).uploadProblemData(any(), any());
        assertThat(problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, PACK_PID))).isNull();
    }

    /**
     * 题面没有样例时应返回 400：评测能跑但题面不可用，不如让出题人先补齐。
     */
    @Test
    void importShouldRejectStatementWithoutSample() throws Exception {
        Map<String, String> entries = defaultPackEntries();
        entries.put("problem_zh.md", """
                ## 题目描述

                只有描述，没有样例。
                """);

        mockMvc.perform(importRequest(entries, PACK_PID, true, 10001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        verify(judgeClient, never()).uploadProblemData(any(), any());
    }

    /**
     * 特判题的 checker 源码不在题目包内时，应在调用评测端前就返回 400。
     */
    @Test
    void importShouldRejectSpecialPackWithoutChecker() throws Exception {
        Map<String, String> entries = defaultPackEntries();
        entries.remove("testdata/checker.cpp");

        mockMvc.perform(importRequest(entries, PACK_PID, true, 10001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        verify(judgeClient, never()).updateProblem(any());
        verify(judgeClient, never()).uploadProblemData(any(), any());
        assertThat(problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, PACK_PID))).isNull();
    }

    /**
     * 题号非法时应返回 400。
     */
    @Test
    void importShouldRejectInvalidPid() throws Exception {
        mockMvc.perform(importRequest(defaultPackEntries(), "bad-pid", true, 10001L))
                .andExpect(status().isBadRequest());

        verify(judgeClient, never()).uploadProblemData(any(), any());
    }

    /**
     * 学生无权导入题目包。
     */
    @Test
    void studentShouldNotImportProblemPack() throws Exception {
        mockMvc.perform(importRequest(defaultPackEntries(), PACK_PID, true, 10004L))
                .andExpect(status().isForbidden());

        verify(judgeClient, never()).uploadProblemData(any(), any());
    }

    private Map<String, String> defaultPackEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("problem.yaml", PROBLEM_YAML);
        entries.put("problem_zh.md", PROBLEM_MARKDOWN);
        entries.put("testdata/config.yaml", DATA_CONFIG_YAML);
        entries.put("testdata/1.in", "1 2\n");
        entries.put("testdata/1.ans", "3\n");
        entries.put("testdata/checker.cpp", "int main() { return 0; }\n");
        return entries;
    }

    private MockMultipartHttpServletRequestBuilder importRequest(Map<String, String> entries, String pid,
                                                                 boolean isPublic, long userId) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/problem/import");
        builder.file(buildPackFile(entries))
                .param("pid", pid)
                .param("is_public", String.valueOf(isPublic))
                .header("Authorization", bearerToken(userId));
        return builder;
    }

    private MockMultipartFile buildPackFile(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return new MockMultipartFile("file", "pack.zip", "application/zip", outputStream.toByteArray());
    }

    private Set<String> readZipEntryNames(byte[] zipBytes) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}

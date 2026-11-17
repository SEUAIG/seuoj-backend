package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.model.ProblemCommon;
import com.seuoj.seuojbackend.pack.HydroPackWriter;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProblemExportServiceTest {
    final ProblemService problems = mock(ProblemService.class);
    final ProblemMapper mapper = mock(ProblemMapper.class);
    final JudgeClient judge = mock(JudgeClient.class);
    final ProblemExportService service = new ProblemExportService(problems, mapper, judge, new HydroPackWriter());

    @BeforeEach void setup() {
        ProblemDetailVO detail = new ProblemDetailVO();
        detail.setPid("P1001");
        detail.setTitle("任意拆分");
        when(mapper.getProblemDetail("P1001")).thenReturn(detail);
        when(mapper.getProblemTags("P1001")).thenReturn(List.of("数学"));

        ProblemContentDTO content = new ProblemContentDTO();
        content.setDescription("给定 n。");
        ProblemCommon.Example example = new ProblemCommon.Example();
        example.setIn("4");
        example.setAns("1 3");
        content.setExample(List.of(example));
        when(judge.fetchProblemContent("P1001")).thenReturn(content);

        ProblemConfigDTO config = new ProblemConfigDTO();
        ProblemConfigDTO.ProblemInfo info = new ProblemConfigDTO.ProblemInfo();
        info.setProblemType("Special");
        info.setCheckerType("Special");
        config.setProblemInfo(info);
        ProblemConfigDTO.CustomModules modules = new ProblemConfigDTO.CustomModules();
        modules.setCheckerPath("checker.cpp");
        config.setCustomModules(modules);
        ProblemConfigDTO.Testcase testcase = new ProblemConfigDTO.Testcase();
        testcase.setId(1);
        testcase.setInPath("1.in");
        testcase.setAnsPath("1.ans");
        testcase.setWeight(100);
        config.setTestcases(List.of(testcase));
        when(judge.fetchProblemConfig("P1001")).thenReturn(config);
    }

    @Test void packHasStatementConfigAndDataButNoCompiledChecker() throws IOException {
        byte[] data = zip(Map.of("1.in", "4", "1.ans", "1 3", "checker.cpp", "// src", "checker", "ELF",
                "config.yaml", "stale", "../evil", "x"));
        doAnswer(inv -> {
            inv.<JudgeClient.ZipStreamReader>getArgument(1).read(new ByteArrayInputStream(data));
            return null;
        }).when(judge).readProblemDataZip(eq("P1001"), any());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(service.prepare("P1001"), out);
        Map<String, String> entries = unzip(out.toByteArray());

        assertEquals(List.of("problem.yaml", "problem_zh.md", "testdata/config.yaml",
                "testdata/1.ans", "testdata/1.in", "testdata/checker.cpp"), List.copyOf(entries.keySet()));
        assertTrue(entries.get("problem.yaml").contains("任意拆分"));
        assertTrue(entries.get("problem_zh.md").contains("```input1\n4\n```"));
        assertTrue(entries.get("testdata/config.yaml").contains("checker: checker.cpp"));
        assertEquals("4", entries.get("testdata/1.in"));
    }

    @Test void exportedPackImportsBackThroughTheRealImporter() throws IOException {
        byte[] data = zip(Map.of("1.in", "4", "1.ans", "1 3", "checker.cpp", "// src", "checker", "ELF"));
        doAnswer(inv -> {
            inv.<JudgeClient.ZipStreamReader>getArgument(1).read(new ByteArrayInputStream(data));
            return null;
        }).when(judge).readProblemDataZip(eq("P1001"), any());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(service.prepare("P1001"), out);

        ProblemService target = mock(ProblemService.class);
        JudgeClient targetJudge = mock(JudgeClient.class);
        com.seuoj.seuojbackend.mapper.TagMapper tags = mock(com.seuoj.seuojbackend.mapper.TagMapper.class);
        when(tags.selectList(any())).thenReturn(List.of(new com.seuoj.seuojbackend.entity.Tag().setId(3L).setTagName("数学")));
        var created = new com.seuoj.seuojbackend.vo.problem.ProblemCreateVO();
        created.setPid("P2001");
        when(target.createProblem(any())).thenReturn(created);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ProblemImportService importer = new ProblemImportService(
                new com.seuoj.seuojbackend.archive.ProblemArchiveExtractor(new com.seuoj.seuojbackend.config.ProblemTestcaseProperties()),
                target, targetJudge, tags,
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(),
                new com.seuoj.seuojbackend.pack.HydroStatementParser(),
                new com.seuoj.seuojbackend.pack.HydroJudgeConfigConverter(mapper));

        var result = importer.importPack(new org.springframework.mock.web.MockMultipartFile(
                "file", "P1001.zip", "application/zip", out.toByteArray()), null, "P2001", false);

        assertTrue(result.getWarnings().stream().noneMatch(w -> w.contains("无法")), result.getWarnings().toString());
        var dto = org.mockito.ArgumentCaptor.forClass(com.seuoj.seuojbackend.dto.problem.ProblemCreateDTO.class);
        verify(target).createProblem(dto.capture());
        assertEquals("任意拆分", dto.getValue().getTitle());
        assertEquals(List.of(3L), dto.getValue().getTags());
        assertEquals("给定 n。", dto.getValue().getDescription());
        assertEquals("1 3", dto.getValue().getExample().get(0).getAns());

        var uploaded = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(targetJudge).uploadProblemData(eq("P2001"), uploaded.capture());
        assertEquals(List.of("1.ans", "1.in", "checker.cpp"), List.copyOf(unzip(uploaded.getValue()).keySet()));
        var config = org.mockito.ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(targetJudge).updateProblemConfig(eq("P2001"), config.capture());
        assertEquals("Special", config.getValue().path("problem_info").path("checker_type").asText());
        assertEquals("checker.cpp", config.getValue().path("custom_modules").path("checker_path").asText());
        assertEquals(100, config.getValue().path("testcases").get(0).path("weight").asInt());
    }

    @Test void exportNeedsManagePermissionBeforeTouchingTheJudge() {
        doThrow(new ForbiddenException("no")).when(problems).assertCanManageProblem("P1001");
        assertThrows(ForbiddenException.class, () -> service.prepare("P1001"));
        verifyNoInteractions(judge);
    }

    private static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("subdir/"));
            zip.closeEntry();
            for (var file : new java.util.TreeMap<>(files).entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}

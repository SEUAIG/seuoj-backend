package com.seuoj.seuojbackend.unit.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.pack.HydroJudgeConfigConverter;
import com.seuoj.seuojbackend.pack.HydroYaml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HydroJudgeConfigConverterTest {

    private final HydroJudgeConfigConverter converter = new HydroJudgeConfigConverter(new ObjectMapper());

    private final List<String> warnings = new ArrayList<>();

    @Test
    void shouldDiscoverCasesInNaturalOrderAndSplitWeightsTo100() {
        HydroJudgeConfigConverter.Result result = converter.convert(
                config("""
                        type: default
                        checker_type: default
                        time: 1s
                        memory: 256m
                        """),
                entries("1.in", "1.ans", "2.in", "2.ans", "10.in", "10.ans", "config.yaml"),
                warnings);

        assertThat(result.testcaseCount()).isEqualTo(3);
        assertThat(result.special()).isFalse();

        JsonNode config = result.config();
        assertThat(config.path("problem_info").path("problem_type").asText()).isEqualTo("Standard");
        assertThat(config.path("problem_info").path("checker_type").asText()).isEqualTo("Standard");
        assertThat(config.path("problem_info").path("time_limit_ms").asLong()).isEqualTo(1000L);
        assertThat(config.path("problem_info").path("memory_limit_kb").asLong()).isEqualTo(262144L);
        assertThat(inPaths(config)).containsExactly("1.in", "2.in", "10.in");
        assertThat(weights(config)).containsExactly(34, 33, 33);
        assertThat(config.path("subtasks")).isEmpty();
        assertThat(config.path("custom_modules").path("checker_path").isMissingNode()).isTrue();
    }

    @Test
    void shouldUseDefaultLimitsWithoutConfig() {
        HydroJudgeConfigConverter.Result result =
                converter.convert(Map.of(), entries("1.in", "1.ans"), warnings);

        assertThat(result.config().path("problem_info").path("time_limit_ms").asLong()).isEqualTo(1000L);
        assertThat(result.config().path("problem_info").path("memory_limit_kb").asLong()).isEqualTo(262144L);
        assertThat(weights(result.config())).containsExactly(100);
    }

    @Test
    void shouldParseTimeAndMemoryUnits() {
        JsonNode problemInfo = converter.convert(
                config("""
                        time: 1.5s
                        memory: 131072k
                        """),
                entries("1.in", "1.ans"), warnings).config().path("problem_info");

        assertThat(problemInfo.path("time_limit_ms").asLong()).isEqualTo(1500L);
        assertThat(problemInfo.path("memory_limit_kb").asLong()).isEqualTo(131072L);
    }

    @Test
    void shouldResolveSpecialJudgeFromChecker() {
        HydroJudgeConfigConverter.Result result = converter.convert(
                config("""
                        checker_type: testlib
                        checker: checker.cpp
                        """),
                entries("1.in", "1.ans", "checker.cpp"), warnings);

        assertThat(result.special()).isTrue();
        assertThat(result.config().path("problem_info").path("problem_type").asText()).isEqualTo("Special");
        assertThat(result.config().path("problem_info").path("checker_type").asText()).isEqualTo("Special");
        assertThat(result.config().path("custom_modules").path("checker_path").asText()).isEqualTo("checker.cpp");
    }

    @Test
    void shouldAllowMissingAnswerFileOnlyForSpecialJudge() {
        HydroJudgeConfigConverter.Result result = converter.convert(
                config("""
                        checker_type: testlib
                        checker: checker.cpp
                        """),
                entries("1.in", "checker.cpp"), warnings);

        assertThat(result.config().path("testcases").get(0).path("ans_path").asText()).isEmpty();
        assertThat(warnings).anyMatch(warning -> warning.contains("1.in"));

        assertThatThrownBy(() -> converter.convert(Map.of(), entries("1.in"), warnings))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1.ans");
    }

    @Test
    void shouldResolveInteractiveProblem() {
        HydroJudgeConfigConverter.Result result = converter.convert(
                config("""
                        type: interactive
                        interactor: interactor.cpp
                        """),
                entries("1.in", "1.ans", "interactor.cpp"), warnings);

        assertThat(result.config().path("problem_info").path("problem_type").asText()).isEqualTo("Interactive");
        assertThat(result.config().path("problem_info").path("checker_type").asText()).isEqualTo("Interactor");
        assertThat(result.config().path("custom_modules").path("interactor_path").asText())
                .isEqualTo("interactor.cpp");
    }

    @Test
    void shouldConvertSubtasks() {
        JsonNode config = converter.convert(
                config("""
                        subtasks:
                          - id: 1
                            score: 40
                            type: min
                            cases:
                              - input: 1.in
                                output: 1.ans
                          - id: 2
                            score: 60
                            if: [1]
                            cases:
                              - input: 2.in
                                output: 2.ans
                        """),
                entries("1.in", "1.ans", "2.in", "2.ans"), warnings).config();

        assertThat(inPaths(config)).containsExactly("1.in", "2.in");
        JsonNode subtasks = config.path("subtasks");
        assertThat(subtasks).hasSize(2);
        assertThat(subtasks.get(0).path("cases").get(0).asInt()).isEqualTo(1);
        assertThat(subtasks.get(0).path("pre_subtasks")).isEmpty();
        assertThat(subtasks.get(1).path("cases").get(0).asInt()).isEqualTo(2);
        assertThat(subtasks.get(1).path("pre_subtasks").get(0).asInt()).isEqualTo(1);
        assertThat(subtasks.get(1).path("type").asText()).isEqualTo("min");
    }

    @Test
    void shouldRejectSubtaskScoresNotSummingTo100() {
        assertThatThrownBy(() -> converter.convert(
                config("""
                        subtasks:
                          - score: 40
                            cases:
                              - input: 1.in
                                output: 1.ans
                        """),
                entries("1.in", "1.ans"), warnings))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("恰好为 100");
    }

    @Test
    void shouldRejectUnsupportedSubtaskType() {
        assertThatThrownBy(() -> converter.convert(
                config("""
                        subtasks:
                          - score: 100
                            type: max
                            cases:
                              - input: 1.in
                                output: 1.ans
                        """),
                entries("1.in", "1.ans"), warnings))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("只支持 min 与 sum");
    }

    @Test
    void shouldRejectFileIoProblem() {
        assertThatThrownBy(() -> converter.convert(
                config("filename: data\n"), entries("1.in", "1.ans"), warnings))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不支持文件输入输出");
    }

    @Test
    void shouldRejectMissingCheckerFile() {
        assertThatThrownBy(() -> converter.convert(
                config("checker: checker.cpp\n"), entries("1.in", "1.ans"), warnings))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("checker.cpp");
    }

    @Test
    void shouldWarnAboutUnknownType() {
        HydroJudgeConfigConverter.Result result =
                converter.convert(config("type: testlib\n"), entries("1.in", "1.ans"), warnings);

        assertThat(result.config().path("problem_info").path("problem_type").asText()).isEqualTo("Standard");
        assertThat(warnings).anyMatch(warning -> warning.contains("无法识别"));
    }

    private Map<String, Object> config(String yaml) {
        return HydroYaml.loadMap(yaml, "testdata/config.yaml");
    }

    @Test
    void shouldImport256CasesInOneAcmSubtask() {
        List<Map<String, Object>> cases = new ArrayList<>();
        Set<String> files = new LinkedHashSet<>();
        for (int i = 1; i <= 256; i++) {
            cases.add(Map.of("input", i + ".in", "output", i + ".ans"));
            files.add(i + ".in");
            files.add(i + ".ans");
        }
        Map<String, Object> config = Map.of("subtasks", List.of(
                Map.of("id", 1, "score", 100, "type", "min", "cases", cases)));
        var result = converter.convert(config, files, warnings);
        assertThat(result.testcaseCount()).isEqualTo(256);
        assertThat(weights(result.config())).hasSize(256).containsOnly(1);
        assertThat(result.config().path("subtasks").get(0).path("cases")).hasSize(256);
        assertThat(result.config().path("subtasks").get(0).path("score").asInt()).isEqualTo(100);
    }

    @Test
    void shouldKeepLegacyWeightedCaseLimitWithoutSubtasks() {
        Set<String> files = new LinkedHashSet<>();
        for (int i = 1; i <= 101; i++) {
            files.add(i + ".in");
            files.add(i + ".ans");
        }
        assertThatThrownBy(() -> converter.convert(Map.of(), files, warnings))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("超过上限 100");
    }

    private Set<String> entries(String... names) {
        return new LinkedHashSet<>(List.of(names));
    }

    private List<String> inPaths(JsonNode config) {
        List<String> paths = new ArrayList<>();
        config.path("testcases").forEach(testcase -> paths.add(testcase.path("in_path").asText()));
        return paths;
    }

    private List<Integer> weights(JsonNode config) {
        List<Integer> values = new ArrayList<>();
        config.path("testcases").forEach(testcase -> values.add(testcase.path("weight").asInt()));
        return values;
    }
}

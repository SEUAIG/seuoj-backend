package com.seuoj.seuojbackend.unit.pack;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.model.ProblemCommon;
import com.seuoj.seuojbackend.pack.HydroJudgeConfigConverter;
import com.seuoj.seuojbackend.pack.HydroPackWriter;
import com.seuoj.seuojbackend.pack.HydroStatement;
import com.seuoj.seuojbackend.pack.HydroStatementParser;
import com.seuoj.seuojbackend.pack.HydroYaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 导出的包必须能原样导回：writer 的输出喂给导入器，结果要和原始数据一致 */
class HydroPackWriterTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HydroPackWriter writer = new HydroPackWriter();
    private final HydroStatementParser parser = new HydroStatementParser();
    private final HydroJudgeConfigConverter converter = new HydroJudgeConfigConverter(mapper);
    private final List<String> warnings = new ArrayList<>();

    @Test
    void statementSurvivesRoundTrip() {
        String description = "描述第一段。\n\n## 小节标题\n\n```cpp\n## 代码里的井号不动\n```\n\n结尾。";
        List<ProblemCommon.Example> examples = List.of(
                example("1 2\n", "3", "第一组。\n\n```text\n解释里的代码块\n```"),
                example("```\n  缩进 保留\n", "", ""),
                example("5", "5\n6", null));
        String hint = "### 数据范围\n\n$n \\le 10^5$\n\n### 提示\n\n注意溢出。";

        String md = writer.statement(description, "一行两个整数。", "一行一个整数。", examples, hint);
        HydroStatement back = parser.parse(md, "problem_zh.md", warnings);

        assertThat(back.description()).isEqualTo(description.replace("## 小节标题", "### 小节标题"));
        assertThat(back.input()).isEqualTo("一行两个整数。");
        assertThat(back.output()).isEqualTo("一行一个整数。");
        assertThat(back.hint()).isEqualTo(hint);
        assertThat(back.examples()).hasSize(3);
        assertThat(back.examples().get(0).getIn()).isEqualTo("1 2");
        assertThat(back.examples().get(0).getAns()).isEqualTo("3");
        assertThat(back.examples().get(0).getDescription()).isEqualTo("第一组。\n\n```text\n解释里的代码块\n```");
        assertThat(back.examples().get(1).getIn()).isEqualTo("```\n  缩进 保留");
        assertThat(back.examples().get(1).getAns()).isEmpty();
        assertThat(back.examples().get(2).getAns()).isEqualTo("5\n6");
        assertThat(back.examples().get(2).getDescription()).isEmpty();
        assertThat(warnings).isEmpty();
    }

    @Test
    void manifestKeepsTitleTagsAndNonDerivableStatementType() {
        Map<String, Object> yaml = HydroYaml.loadMap(
                writer.manifest("A: B #1", List.of("数学", "SPJ"), "Communication"), "problem.yaml");
        assertThat(yaml).containsEntry("title", "A: B #1")
                .containsEntry("tag", List.of("数学", "SPJ"))
                .containsEntry("statement_type", "Communication");
        assertThat(HydroYaml.loadMap(writer.manifest("T", List.of(), "Standard"), "problem.yaml"))
                .doesNotContainKey("statement_type");
    }

    @Test
    void weightedSpecialGraderProblemSurvivesRoundTrip() {
        ProblemConfigDTO original = config("Standard", "Special", 2000L, 524288L, 4096L);
        original.getCustomModules().setCheckerPath("checker.cpp");
        original.getCustomModules().setGraderPaths(List.of("sum.h", "grader.cpp"));
        original.setTestcases(List.of(
                testcase(1, "1.in", "1.ans", 30, null, null),
                testcase(2, "sub/2.in", "", 70, 3000L, 1000L)));
        original.setSubtasks(List.of());

        ProblemConfigDTO back = roundTrip(original, files("1.in", "1.ans", "sub/2.in", "checker.cpp", "sum.h", "grader.cpp"));

        // 题型与判定方式由导入器从 checker 推出，Special 题在评测端是 Special/Special
        assertThat(back.getProblemInfo().getCheckerType()).isEqualTo("Special");
        assertThat(back.getProblemInfo().getTimeLimitMs()).isEqualTo(2000L);
        assertThat(back.getProblemInfo().getMemoryLimitKb()).isEqualTo(524288L);
        assertThat(back.getProblemInfo().getMaxCodeLength()).isEqualTo(4096L);
        assertThat(back.getCustomModules().getCheckerPath()).isEqualTo("checker.cpp");
        assertThat(back.getCustomModules().getGraderPaths()).containsExactly("sum.h", "grader.cpp");
        assertThat(back.getTestcases()).extracting(ProblemConfigDTO.Testcase::getInPath).containsExactly("1.in", "sub/2.in");
        assertThat(back.getTestcases()).extracting(ProblemConfigDTO.Testcase::getWeight).containsExactly(30, 70);
        assertThat(back.getTestcases().get(1).getAnsPath()).isEmpty();
        assertThat(back.getTestcases().get(1).getTimeLimitMs()).isEqualTo(3000L);
        assertThat(back.getTestcases().get(1).getMemoryLimitKb()).isEqualTo(1000L);
        assertThat(back.getSubtasks()).isEmpty();
    }

    @Test
    void subtasksWithGapsInCaseIdsSurviveRoundTrip() {
        ProblemConfigDTO original = config("Standard", "Standard", 1000L, 262144L, 65536L);
        original.setTestcases(List.of(
                testcase(1, "a.in", "a.ans", 1, null, null),
                testcase(5, "b.in", "b.ans", 1, null, null),
                testcase(9, "c.in", "c.ans", 1, null, null)));
        original.setSubtasks(List.of(
                subtask(1, List.of(1, 5), List.of(), 40, "min"),
                subtask(2, List.of(9), List.of(1), 60, "sum")));

        ProblemConfigDTO back = roundTrip(original, files("a.in", "a.ans", "b.in", "b.ans", "c.in", "c.ans"));

        Map<Integer, String> path = new HashMap<>();
        back.getTestcases().forEach(testcase -> path.put(testcase.getId(), testcase.getInPath()));
        assertThat(back.getSubtasks()).hasSize(2);
        assertThat(back.getSubtasks().get(0).getCases().stream().map(path::get)).containsExactly("a.in", "b.in");
        assertThat(back.getSubtasks().get(0).getScore()).isEqualTo(40);
        assertThat(back.getSubtasks().get(1).getCases().stream().map(path::get)).containsExactly("c.in");
        assertThat(back.getSubtasks().get(1).getPreSubtasks()).containsExactly(1);
        assertThat(back.getSubtasks().get(1).getType()).isEqualTo("sum");
        assertThat(warnings).isEmpty();
    }

    @Test
    void interactiveProblemSurvivesRoundTrip() {
        ProblemConfigDTO original = config("Interactive", "Interactor", 1000L, 262144L, -1L);
        original.getCustomModules().setInteractorPath("interactor.cpp");
        original.setTestcases(List.of(testcase(1, "1.in", "1.ans", 100, null, null)));

        ProblemConfigDTO back = roundTrip(original, files("1.in", "1.ans", "interactor.cpp"));

        assertThat(back.getProblemInfo().getProblemType()).isEqualTo("Interactive");
        assertThat(back.getProblemInfo().getCheckerType()).isEqualTo("Interactor");
        assertThat(back.getProblemInfo().getMaxCodeLength()).isEqualTo(-1L);
        assertThat(back.getCustomModules().getInteractorPath()).isEqualTo("interactor.cpp");
    }

    @Test
    void compiledCheckerAndInteractorBinariesAreSkipped() {
        ProblemConfigDTO original = config("Interactive", "Interactor", 1000L, 262144L, null);
        original.getCustomModules().setInteractorPath("judge/interactor.cpp");
        original.getCustomModules().setCheckerPath("checker.cc");
        original.setTestcases(List.of(testcase(1, "checker", "1.ans", 100, null, null)));

        // checker 正好也是某个测试点的输入文件名时不能跳过
        assertThat(writer.compiledArtifacts(original)).containsExactly("judge/interactor");
    }

    private ProblemConfigDTO roundTrip(ProblemConfigDTO original, Set<String> files) {
        Map<String, Object> yaml = HydroYaml.loadMap(writer.config(original), "testdata/config.yaml");
        return mapper.convertValue(converter.convert(yaml, files, warnings).config(), ProblemConfigDTO.class);
    }

    private static ProblemConfigDTO config(String problemType, String checkerType,
                                           Long time, Long memory, Long codeLength) {
        ProblemConfigDTO config = new ProblemConfigDTO();
        ProblemConfigDTO.ProblemInfo info = new ProblemConfigDTO.ProblemInfo();
        info.setProblemType(problemType);
        info.setCheckerType(checkerType);
        info.setTimeLimitMs(time);
        info.setMemoryLimitKb(memory);
        info.setMaxCodeLength(codeLength);
        config.setProblemInfo(info);
        config.setCustomModules(new ProblemConfigDTO.CustomModules());
        return config;
    }

    private static ProblemConfigDTO.Testcase testcase(int id, String in, String ans, int weight,
                                                      Long time, Long memory) {
        ProblemConfigDTO.Testcase testcase = new ProblemConfigDTO.Testcase();
        testcase.setId(id);
        testcase.setInPath(in);
        testcase.setAnsPath(ans);
        testcase.setWeight(weight);
        testcase.setTimeLimitMs(time);
        testcase.setMemoryLimitKb(memory);
        return testcase;
    }

    private static ProblemConfigDTO.Subtask subtask(int id, List<Integer> cases, List<Integer> pre,
                                                    int score, String type) {
        ProblemConfigDTO.Subtask subtask = new ProblemConfigDTO.Subtask();
        subtask.setId(id);
        subtask.setCases(cases);
        subtask.setPreSubtasks(pre);
        subtask.setScore(score);
        subtask.setType(type);
        return subtask;
    }

    private static ProblemCommon.Example example(String in, String ans, String description) {
        ProblemCommon.Example example = new ProblemCommon.Example();
        example.setIn(in);
        example.setAns(ans);
        example.setDescription(description);
        return example;
    }

    private static Set<String> files(String... names) {
        return new LinkedHashSet<>(List.of(names));
    }
}

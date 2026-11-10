package com.seuoj.seuojbackend.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seuoj.seuojbackend.exception.BadRequestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 把 Hydro 的 testdata/config.yaml 翻译成评测端的 info.toml 等价 JSON
 * （problem_info / testcases / subtasks / custom_modules）。
 *
 * <p>Hydro 的 config.yaml 不枚举测试点，靠 testdata 目录里的 .in / .ans 约定；
 * 评测端要求测试点显式列出且 weight 之和恰好为 100，所以这里负责发现测试点并均分权重。</p>
 */
@Slf4j
@Component
public class HydroJudgeConfigConverter {

    private static final String CONFIG_ENTRY = "testdata/config.yaml";

    private static final long DEFAULT_TIME_LIMIT_MS = 1000L;
    private static final long DEFAULT_MEMORY_LIMIT_KB = 256L * 1024;
    private static final long DEFAULT_MAX_CODE_LENGTH = 65536L;
    private static final long MAX_TIME_LIMIT_MS = 60_000L;
    private static final long MAX_MEMORY_LIMIT_KB = 4L * 1024 * 1024;
    /** weight 要均分成总和 100，测试点再多就分不出正整数了 */
    private static final int MAX_TESTCASE_COUNT = 100;
    private static final int TOTAL_SCORE = 100;

    private static final Pattern TIME_VALUE =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(ms|s|sec|secs|second|seconds)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_VALUE =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(b|k|kb|kib|m|mb|mib|g|gb|gib)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    /** Hydro 里代表「传统题」的 type 取值 */
    private static final Set<String> TRADITIONAL_TYPES = Set.of("default", "traditional", "standard");
    private static final Set<String> INTERACTIVE_TYPES = Set.of("interactive", "interaction");
    /** 明确不支持、必须报错的 Hydro 题型 */
    private static final Set<String> UNSUPPORTED_TYPES = Set.of("remote_judge", "submit_answer", "objective");
    /** 评测端的子任务聚合方式只支持这两种 */
    private static final Set<String> SUPPORTED_SUBTASK_TYPES = Set.of("min", "sum");

    private final ObjectMapper objectMapper;

    public HydroJudgeConfigConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param config      testdata/config.yaml 解析结果，缺该文件时传空 Map
     * @param dataEntries testdata/ 下的全部文件名（相对 testdata/）
     * @param warnings    收集不阻断导入的问题
     */
    public Result convert(Map<String, Object> config, Set<String> dataEntries, List<String> warnings) {
        if (StringUtils.hasText(HydroYaml.str(config, "filename"))) {
            throw new BadRequestException("SEUOJ 不支持文件输入输出，请删掉 " + CONFIG_ENTRY + " 里的 filename");
        }

        Judged judged = resolveProblemType(config, dataEntries, warnings);
        long timeLimitMs = parseTimeMs(HydroYaml.str(config, "time"), DEFAULT_TIME_LIMIT_MS, "time");
        long memoryLimitKb = parseMemoryKb(HydroYaml.str(config, "memory"), DEFAULT_MEMORY_LIMIT_KB, "memory");

        List<Map<String, Object>> subtaskDefs = HydroYaml.mapList(config, "subtasks", CONFIG_ENTRY);
        List<CaseSpec> cases = resolveCases(config, subtaskDefs, dataEntries, judged.special(), warnings);
        if (subtaskDefs.isEmpty() && cases.size() > MAX_TESTCASE_COUNT) {
            throw new BadRequestException("测试点数量为 " + cases.size() + "，超过上限 " + MAX_TESTCASE_COUNT
                    + "（weight 需要均分成总和 100）");
        }

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode problemInfo = root.putObject("problem_info");
        problemInfo.put("problem_type", judged.problemType());
        problemInfo.put("checker_type", judged.checkerType());
        problemInfo.put("time_limit_ms", timeLimitMs);
        problemInfo.put("memory_limit_kb", memoryLimitKb);
        problemInfo.put("max_code_length", DEFAULT_MAX_CODE_LENGTH);

        ArrayNode testcases = root.putArray("testcases");
        int[] weights;
        if (subtaskDefs.isEmpty()) {
            weights = distributeWeights(cases.size());
        } else {
            // Subtasks own the scores; case weights are compatibility placeholders.
            weights = new int[cases.size()];
            Arrays.fill(weights, 1);
        }
        for (int i = 0; i < cases.size(); i++) {
            CaseSpec spec = cases.get(i);
            ObjectNode testcase = testcases.addObject();
            testcase.put("id", i + 1);
            testcase.put("in_path", spec.inPath());
            testcase.put("ans_path", spec.ansPath() == null ? "" : spec.ansPath());
            testcase.put("weight", weights[i]);
            if (spec.timeLimitMs() != null) {
                testcase.put("time_limit_ms", spec.timeLimitMs());
            }
            if (spec.memoryLimitKb() != null) {
                testcase.put("memory_limit_kb", spec.memoryLimitKb());
            }
        }

        root.set("subtasks", buildSubtasks(subtaskDefs, cases));

        ObjectNode customModules = root.putObject("custom_modules");
        if (judged.checkerPath() != null) {
            customModules.put("checker_path", judged.checkerPath());
        }
        if (judged.interactorPath() != null) {
            customModules.put("interactor_path", judged.interactorPath());
        }

        return new Result(root, cases.size(), judged.special());
    }

    /**
     * Hydro 的 type / checker_type / checker / interactor 映射到评测端的题型与判定方式
     */
    private Judged resolveProblemType(Map<String, Object> config, Set<String> dataEntries, List<String> warnings) {
        String type = lower(HydroYaml.str(config, "type"));
        String checkerType = lower(HydroYaml.str(config, "checker_type"));
        String checker = HydroYaml.str(config, "checker");
        String interactor = HydroYaml.str(config, "interactor");

        if (type != null && UNSUPPORTED_TYPES.contains(type)) {
            throw new BadRequestException("SEUOJ 不支持 Hydro 题型 type: " + type);
        }
        if (type != null && !TRADITIONAL_TYPES.contains(type) && !INTERACTIVE_TYPES.contains(type)) {
            // createhydro 明确要求 type 只表示题型，不要写成 testlib 之类；这里按传统题继续，但要提醒
            warnings.add(CONFIG_ENTRY + " 的 type: " + type + " 无法识别，已按传统题导入");
            type = null;
        }

        boolean interactive = (type != null && INTERACTIVE_TYPES.contains(type))
                || "interactor".equals(checkerType)
                || interactor != null;
        if (interactive) {
            String path = interactor != null ? interactor : firstExisting(dataEntries, "interactor.cpp");
            if (path == null) {
                throw new BadRequestException("交互题需要在 " + CONFIG_ENTRY + " 中指定 interactor");
            }
            requireDataFile(dataEntries, path, "interactor");
            return new Judged("Interactive", "Interactor", null, path, false);
        }

        boolean special = checker != null || (checkerType != null && !"default".equals(checkerType));
        if (!special) {
            return new Judged("Standard", "Standard", null, null, false);
        }

        if (checkerType != null && !"testlib".equals(checkerType) && !"default".equals(checkerType)) {
            warnings.add(CONFIG_ENTRY + " 的 checker_type: " + checkerType
                    + " 不被支持，已按 testlib checker 导入；如果 checker 不是 testlib 写法请改写后重新导入");
        }
        String path = checker != null ? checker : firstExisting(dataEntries, "checker.cpp", "spj.cpp");
        if (path == null) {
            throw new BadRequestException(CONFIG_ENTRY + " 声明了 checker_type: " + checkerType
                    + "，但没有指定 checker，testdata/ 下也没有 checker.cpp 或 spj.cpp");
        }
        requireDataFile(dataEntries, path, "checker");
        return new Judged("Special", "Special", path, null, true);
    }

    /**
     * 测试点来源优先级：config.yaml 的 cases -> subtasks 里的 cases -> 扫描 testdata 下的 .in / .ans
     */
    private List<CaseSpec> resolveCases(Map<String, Object> config,
                                        List<Map<String, Object>> subtaskDefs,
                                        Set<String> dataEntries,
                                        boolean special,
                                        List<String> warnings) {
        List<CaseSpec> explicit = readCaseList(HydroYaml.mapList(config, "cases", CONFIG_ENTRY),
                dataEntries, special, warnings, CONFIG_ENTRY + " 的 cases");
        if (!explicit.isEmpty()) {
            return explicit;
        }

        if (!subtaskDefs.isEmpty()) {
            List<CaseSpec> fromSubtasks = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < subtaskDefs.size(); i++) {
                String context = CONFIG_ENTRY + " 的 subtasks[" + i + "].cases";
                List<CaseSpec> subtaskCases = readCaseList(
                        HydroYaml.mapList(subtaskDefs.get(i), "cases", CONFIG_ENTRY),
                        dataEntries, special, warnings, context);
                if (subtaskCases.isEmpty()) {
                    throw new BadRequestException(context + " 不能为空：SEUOJ 需要每个子任务显式列出测试点");
                }
                for (CaseSpec spec : subtaskCases) {
                    if (seen.add(spec.inPath())) {
                        fromSubtasks.add(spec);
                    }
                }
            }
            return fromSubtasks;
        }

        return discoverCases(dataEntries, special, warnings);
    }

    private List<CaseSpec> readCaseList(List<Map<String, Object>> defs,
                                        Set<String> dataEntries,
                                        boolean special,
                                        List<String> warnings,
                                        String context) {
        List<CaseSpec> cases = new ArrayList<>(defs.size());
        for (int i = 0; i < defs.size(); i++) {
            Map<String, Object> def = defs.get(i);
            String itemContext = context + "[" + i + "]";
            String inPath = HydroYaml.str(def, "input");
            if (inPath == null) {
                throw new BadRequestException(itemContext + " 缺少 input");
            }
            requireDataFile(dataEntries, inPath, "测试数据输入文件");

            String ansPath = HydroYaml.str(def, "output");
            if (ansPath != null) {
                requireDataFile(dataEntries, ansPath, "测试数据答案文件");
            } else if (!special) {
                throw new BadRequestException(itemContext + " 缺少 output");
            } else {
                warnings.add(itemContext + " 没有指定 output，特判题将不给 checker 传答案文件");
            }

            Long timeLimitMs = null;
            if (HydroYaml.str(def, "time") != null) {
                timeLimitMs = parseTimeMs(HydroYaml.str(def, "time"), DEFAULT_TIME_LIMIT_MS, itemContext + " 的 time");
            }
            Long memoryLimitKb = null;
            if (HydroYaml.str(def, "memory") != null) {
                memoryLimitKb = parseMemoryKb(HydroYaml.str(def, "memory"), DEFAULT_MEMORY_LIMIT_KB,
                        itemContext + " 的 memory");
            }
            cases.add(new CaseSpec(inPath, ansPath, timeLimitMs, memoryLimitKb));
        }
        return cases;
    }

    /**
     * 扫描 testdata 下的 .in，按自然序排列，答案文件优先取 .ans 再取 .out
     */
    private List<CaseSpec> discoverCases(Set<String> dataEntries, boolean special, List<String> warnings) {
        List<String> inputs = dataEntries.stream()
                .filter(name -> lower(name).endsWith(".in"))
                .sorted(naturalOrder())
                .toList();
        if (inputs.isEmpty()) {
            throw new BadRequestException("testdata/ 下没有找到任何 .in 测试数据");
        }

        List<CaseSpec> cases = new ArrayList<>(inputs.size());
        for (String inPath : inputs) {
            String base = inPath.substring(0, inPath.length() - ".in".length());
            String ansPath = firstExisting(dataEntries, base + ".ans", base + ".out");
            if (ansPath == null) {
                if (!special) {
                    throw new BadRequestException("测试数据 " + inPath + " 没有对应的 " + base + ".ans");
                }
                warnings.add("测试数据 " + inPath + " 没有对应的 " + base + ".ans，特判题将不给 checker 传答案文件");
            }
            cases.add(new CaseSpec(inPath, ansPath, null, null));
        }
        return cases;
    }

    /**
     * Hydro 的 subtasks 映射成评测端的子任务：cases 换成测试点 id，if 换成 pre_subtasks
     */
    private ArrayNode buildSubtasks(List<Map<String, Object>> subtaskDefs, List<CaseSpec> cases) {
        ArrayNode subtasks = objectMapper.createArrayNode();
        if (subtaskDefs.isEmpty()) {
            return subtasks;
        }

        Map<String, Integer> caseIdByInput = new LinkedHashMap<>();
        for (int i = 0; i < cases.size(); i++) {
            caseIdByInput.put(cases.get(i).inPath(), i + 1);
        }

        int scoreSum = 0;
        Set<Integer> usedIds = new LinkedHashSet<>();
        for (int i = 0; i < subtaskDefs.size(); i++) {
            Map<String, Object> def = subtaskDefs.get(i);
            String context = CONFIG_ENTRY + " 的 subtasks[" + i + "]";

            Integer score = HydroYaml.intValue(def, "score", context);
            if (score == null) {
                throw new BadRequestException(context + " 缺少 score");
            }
            Integer id = HydroYaml.intValue(def, "id", context);
            if (id == null) {
                id = i + 1;
            }
            if (!usedIds.add(id)) {
                throw new BadRequestException(context + " 的子任务 id 重复: " + id);
            }

            String type = lower(HydroYaml.str(def, "type"));
            if (type == null) {
                type = "min";
            }
            if (!SUPPORTED_SUBTASK_TYPES.contains(type)) {
                throw new BadRequestException(context + " 的 type: " + type
                        + " 不被支持，SEUOJ 评测端只支持 min 与 sum");
            }

            ObjectNode subtask = subtasks.addObject();
            subtask.put("id", id);
            ArrayNode caseIds = subtask.putArray("cases");
            for (Map<String, Object> caseDef : HydroYaml.mapList(def, "cases", CONFIG_ENTRY)) {
                String inPath = HydroYaml.str(caseDef, "input");
                Integer caseId = inPath == null ? null : caseIdByInput.get(inPath);
                if (caseId == null) {
                    throw new BadRequestException(context + " 引用了不存在的测试点: " + inPath);
                }
                caseIds.add(caseId);
            }
            if (caseIds.isEmpty()) {
                throw new BadRequestException(context + " 的 cases 不能为空");
            }
            ArrayNode preSubtasks = subtask.putArray("pre_subtasks");
            for (Integer pre : HydroYaml.intList(def, "if", context)) {
                preSubtasks.add(pre);
            }
            subtask.put("score", score);
            subtask.put("type", type);
            scoreSum += score;
        }

        if (scoreSum != TOTAL_SCORE) {
            throw new BadRequestException("子任务 score 之和为 " + scoreSum + "，评测端要求恰好为 " + TOTAL_SCORE);
        }
        return subtasks;
    }

    /**
     * 把 100 分尽量均分给每个测试点，余数分给前面几个，保证总和恰好为 100
     */
    private int[] distributeWeights(int count) {
        int[] weights = new int[count];
        int base = TOTAL_SCORE / count;
        int remainder = TOTAL_SCORE % count;
        for (int i = 0; i < count; i++) {
            weights[i] = base + (i < remainder ? 1 : 0);
        }
        return weights;
    }

    /**
     * 解析 Hydro 的时限写法：1s / 1.5s / 1000ms，不带单位按毫秒
     */
    long parseTimeMs(String raw, long defaultValue, String context) {
        if (raw == null) {
            return defaultValue;
        }
        Matcher matcher = TIME_VALUE.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new BadRequestException(context + " 无法解析: " + raw + "（支持 1s / 1500ms 这类写法）");
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = lower(matcher.group(2));
        if (unit != null && !"ms".equals(unit)) {
            value *= 1000;
        }
        long result = Math.round(value);
        if (result <= 0 || result > MAX_TIME_LIMIT_MS) {
            throw new BadRequestException(context + " 超出允许范围（0, " + MAX_TIME_LIMIT_MS + "] ms: " + raw);
        }
        return result;
    }

    /**
     * 解析 Hydro 的内存写法：256m / 256MB / 262144k，不带单位按 MB
     */
    long parseMemoryKb(String raw, long defaultValue, String context) {
        if (raw == null) {
            return defaultValue;
        }
        Matcher matcher = MEMORY_VALUE.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new BadRequestException(context + " 无法解析: " + raw + "（支持 256m / 262144k 这类写法）");
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = lower(matcher.group(2));
        double kb;
        if (unit == null) {
            kb = value * 1024;
        } else {
            kb = switch (unit.charAt(0)) {
                case 'b' -> value / 1024;
                case 'k' -> value;
                case 'm' -> value * 1024;
                default -> value * 1024 * 1024;
            };
        }
        long result = Math.round(kb);
        if (result <= 0 || result > MAX_MEMORY_LIMIT_KB) {
            throw new BadRequestException(context + " 超出允许范围（0, " + MAX_MEMORY_LIMIT_KB + "] KB: " + raw);
        }
        return result;
    }

    private void requireDataFile(Set<String> dataEntries, String path, String label) {
        if (!dataEntries.contains(path)) {
            throw new BadRequestException("testdata/ 下不存在" + label + "文件: " + path);
        }
    }

    private String firstExisting(Set<String> dataEntries, String... candidates) {
        for (String candidate : candidates) {
            if (dataEntries.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 自然序：1.in < 2.in < 10.in，而不是字典序的 1.in < 10.in < 2.in
     */
    private Comparator<String> naturalOrder() {
        return (left, right) -> {
            Matcher leftMatcher = DIGITS.matcher(left);
            Matcher rightMatcher = DIGITS.matcher(right);
            int leftCursor = 0;
            int rightCursor = 0;
            while (leftMatcher.find() && rightMatcher.find()) {
                int prefixCompare = left.substring(leftCursor, leftMatcher.start())
                        .compareTo(right.substring(rightCursor, rightMatcher.start()));
                if (prefixCompare != 0) {
                    return prefixCompare;
                }
                int numberCompare = new java.math.BigInteger(leftMatcher.group(1))
                        .compareTo(new java.math.BigInteger(rightMatcher.group(1)));
                if (numberCompare != 0) {
                    return numberCompare;
                }
                leftCursor = leftMatcher.end();
                rightCursor = rightMatcher.end();
            }
            return left.substring(leftCursor).compareTo(right.substring(rightCursor));
        };
    }

    private String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @param config        评测端配置 JSON
     * @param testcaseCount 测试点数量
     * @param special       是否特判题（评测端会在写配置时编译 checker）
     */
    public record Result(ObjectNode config, int testcaseCount, boolean special) {
    }

    private record Judged(String problemType,
                          String checkerType,
                          String checkerPath,
                          String interactorPath,
                          boolean special) {
    }

    private record CaseSpec(String inPath, String ansPath, Long timeLimitMs, Long memoryLimitKb) {
    }
}

package com.seuoj.seuojbackend.pack;

import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.model.ProblemCommon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析 Hydro 风格的中文题面 Markdown，拆成 SEUOJ 的 description / input / output / example / hint
 *
 * <p>章节按一级、二级标题切分，三级及更深的标题留在所属章节内；切分时跳过围栏代码块，
 * 所以「## 样例」里的围栏块不会被误当成正文边界。</p>
 */
@Slf4j
@Component
public class HydroStatementParser {

    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,2})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}([`]{3,}|[~]{3,})\\s*(.*)$");
    private static final Pattern SAMPLE_INPUT_INFO =
            Pattern.compile("^(?:input|in)\\s*(\\d*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAMPLE_OUTPUT_INFO =
            Pattern.compile("^(?:output|out|ans|answer)\\s*(\\d*)$", Pattern.CASE_INSENSITIVE);
    /** 样例解释标题，例如「样例解释」「样例 2 说明」「解释样例1」 */
    private static final Pattern EXPLAIN_TITLE =
            Pattern.compile("^(?:样例(\\d*)(?:的)?(?:解释|说明)|(?:解释|说明)样例(\\d*))$");
    /** 样例解释章节内部按「### 样例 N」再细分 */
    private static final Pattern EXPLAIN_SUB_HEADING =
            Pattern.compile("^ {0,3}#{3,6}\\s*样例\\s*(\\d+).*$");

    private static final Set<String> BACKGROUND_TITLES = Set.of("题目背景", "背景");
    private static final Set<String> DESCRIPTION_TITLES = Set.of("题目描述", "描述", "题面", "题目", "问题描述");
    private static final Set<String> INPUT_TITLES = Set.of("输入格式", "输入", "输入描述", "输入数据", "输入说明");
    private static final Set<String> OUTPUT_TITLES = Set.of("输出格式", "输出", "输出描述", "输出数据", "输出说明");
    private static final Set<String> SAMPLE_TITLES = Set.of("样例", "样例数据", "输入输出样例", "样例输入输出");
    private static final Set<String> LIMIT_TITLES = Set.of("数据范围", "数据范围与约定", "数据范围与提示",
            "数据规模与约定", "数据规模和约定", "数据规模", "限制与约定", "约定与限制", "数据约定");
    private static final Set<String> HINT_TITLES = Set.of("提示", "说明", "备注", "注意", "提示与说明");

    /**
     * @param markdown  题面 Markdown 原文
     * @param entryName 题面在题目包中的文件名，只用于报错提示
     * @param warnings  收集不阻断导入的问题，最终回给前端提示出题人
     */
    public HydroStatement parse(String markdown, String entryName, List<String> warnings) {
        List<Section> sections = splitSections(stripFrontMatter(normalize(markdown)));

        StringBuilder background = new StringBuilder();
        StringBuilder description = new StringBuilder();
        StringBuilder input = new StringBuilder();
        StringBuilder output = new StringBuilder();
        StringBuilder samples = new StringBuilder();
        StringBuilder limits = new StringBuilder();
        StringBuilder hints = new StringBuilder();
        // 按样例序号收集解释，键为 0 表示没有指明序号
        Map<Integer, StringBuilder> explanations = new TreeMap<>();
        Map<String, StringBuilder> unknownSections = new LinkedHashMap<>();

        for (Section section : sections) {
            String body = section.body().strip();
            String title = normalizeTitle(section.title());
            if (section.title() == null) {
                // 首个标题之前的正文，当成题目描述的开头
                append(description, body);
                continue;
            }
            if (body.isEmpty()) {
                continue;
            }

            if (BACKGROUND_TITLES.contains(title)) {
                append(background, body);
            } else if (DESCRIPTION_TITLES.contains(title)) {
                append(description, body);
            } else if (INPUT_TITLES.contains(title)) {
                append(input, body);
            } else if (OUTPUT_TITLES.contains(title)) {
                append(output, body);
            } else if (SAMPLE_TITLES.contains(title)) {
                append(samples, section.body());
            } else if (LIMIT_TITLES.contains(title)) {
                append(limits, body);
            } else if (HINT_TITLES.contains(title)) {
                append(hints, body);
            } else {
                Matcher explainMatcher = EXPLAIN_TITLE.matcher(title);
                if (explainMatcher.matches()) {
                    String index = StringUtils.hasText(explainMatcher.group(1))
                            ? explainMatcher.group(1)
                            : explainMatcher.group(2);
                    collectExplanation(explanations, index, body);
                } else {
                    append(unknownSections.computeIfAbsent(section.title(), key -> new StringBuilder()), body);
                }
            }
        }

        List<ProblemCommon.Example> examples = buildExamples(extractFences(samples.toString()));
        if (examples.isEmpty()) {
            throw new BadRequestException(entryName + " 里没有解析到样例，"
                    + "请在「## 样例」下用带编号的 input1 / output1 围栏代码块给出样例");
        }
        // 「## 样例」里代码块之外的文字也当成样例解释
        collectExplanation(explanations, "", stripFences(samples.toString()));
        applyExplanations(examples, explanations, hints);

        String finalDescription = joinBlocks(background.toString(), description.toString());
        if (!StringUtils.hasText(finalDescription)) {
            throw new BadRequestException(entryName + " 缺少「## 题目描述」");
        }
        if (!StringUtils.hasText(input.toString())) {
            warnings.add(entryName + " 缺少「## 输入格式」，导入后请在题面编辑页补上");
        }
        if (!StringUtils.hasText(output.toString())) {
            warnings.add(entryName + " 缺少「## 输出格式」，导入后请在题面编辑页补上");
        }
        if (!StringUtils.hasText(limits.toString())) {
            warnings.add(entryName + " 缺少「## 数据范围」，导入后请在题面编辑页补上");
        }
        for (String title : unknownSections.keySet()) {
            warnings.add("题面章节「" + title + "」无法对应到 SEUOJ 字段，已并入「数据范围与提示」");
        }

        return new HydroStatement(finalDescription,
                input.toString().strip(),
                output.toString().strip(),
                buildHint(limits.toString().strip(), hints.toString().strip(), unknownSections),
                examples);
    }

    /**
     * hint 对应前端的「数据范围与提示」一栏。只有一段时直接给正文，多段时用三级标题分隔
     */
    private String buildHint(String limits, String hints, Map<String, StringBuilder> unknownSections) {
        List<String[]> parts = new ArrayList<>();
        if (StringUtils.hasText(limits)) {
            parts.add(new String[]{"数据范围", limits});
        }
        if (StringUtils.hasText(hints)) {
            parts.add(new String[]{"提示", hints});
        }
        for (Map.Entry<String, StringBuilder> entry : unknownSections.entrySet()) {
            String body = entry.getValue().toString().strip();
            if (StringUtils.hasText(body)) {
                parts.add(new String[]{entry.getKey(), body});
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0)[1];
        }

        StringBuilder hint = new StringBuilder();
        for (String[] part : parts) {
            if (hint.length() > 0) {
                hint.append("\n\n");
            }
            hint.append("### ").append(part[0]).append("\n\n").append(part[1]);
        }
        return hint.toString();
    }

    /**
     * 把样例解释写回对应样例；没指明序号且样例不止一个时退化成放进提示，避免张冠李戴
     */
    private void applyExplanations(List<ProblemCommon.Example> examples,
                                   Map<Integer, StringBuilder> explanations,
                                   StringBuilder hints) {
        for (Map.Entry<Integer, StringBuilder> entry : explanations.entrySet()) {
            String body = entry.getValue().toString().strip();
            if (!StringUtils.hasText(body)) {
                continue;
            }
            int index = entry.getKey();
            if (index == 0 && examples.size() > 1) {
                append(hints, body);
                continue;
            }
            int target = index == 0 ? 1 : index;
            if (target >= 1 && target <= examples.size()) {
                ProblemCommon.Example example = examples.get(target - 1);
                example.setDescription(joinBlocks(example.getDescription(), body));
            } else {
                append(hints, body);
            }
        }
    }

    /**
     * 样例解释章节内部可能再按「### 样例 N」细分，拆开分别归属
     */
    private void collectExplanation(Map<Integer, StringBuilder> explanations, String index, String body) {
        if (!StringUtils.hasText(body)) {
            return;
        }
        int current = StringUtils.hasText(index) ? Integer.parseInt(index) : 0;
        StringBuilder buffer = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            Matcher matcher = EXPLAIN_SUB_HEADING.matcher(line);
            if (matcher.matches()) {
                flushExplanation(explanations, current, buffer);
                current = Integer.parseInt(matcher.group(1));
                continue;
            }
            buffer.append(line).append('\n');
        }
        flushExplanation(explanations, current, buffer);
    }

    private void flushExplanation(Map<Integer, StringBuilder> explanations, int index, StringBuilder buffer) {
        String text = buffer.toString().strip();
        buffer.setLength(0);
        if (!text.isEmpty()) {
            append(explanations.computeIfAbsent(index, key -> new StringBuilder()), text);
        }
    }

    /**
     * 优先按 input1 / output1 的编号配对；没有编号信息时按代码块出现顺序两两配对
     */
    private List<ProblemCommon.Example> buildExamples(List<Fence> fences) {
        List<int[]> inputIndexes = new ArrayList<>();
        List<int[]> outputIndexes = new ArrayList<>();
        for (int i = 0; i < fences.size(); i++) {
            String info = fences.get(i).info().trim();
            Matcher inputMatcher = SAMPLE_INPUT_INFO.matcher(info);
            if (inputMatcher.matches()) {
                inputIndexes.add(new int[]{i, parseIndex(inputMatcher.group(1))});
                continue;
            }
            Matcher outputMatcher = SAMPLE_OUTPUT_INFO.matcher(info);
            if (outputMatcher.matches()) {
                outputIndexes.add(new int[]{i, parseIndex(outputMatcher.group(1))});
            }
        }

        if (!inputIndexes.isEmpty()) {
            return pairLabelledFences(fences, inputIndexes, outputIndexes);
        }

        // 没有 input / output 标注时，退化成相邻代码块两两配对
        List<ProblemCommon.Example> examples = new ArrayList<>();
        for (int i = 0; i < fences.size(); i += 2) {
            String ans = i + 1 < fences.size() ? fences.get(i + 1).content() : "";
            examples.add(newExample(fences.get(i).content(), ans));
        }
        return examples;
    }

    private List<ProblemCommon.Example> pairLabelledFences(List<Fence> fences,
                                                           List<int[]> inputIndexes,
                                                           List<int[]> outputIndexes) {
        boolean numbered = inputIndexes.stream().allMatch(item -> item[1] > 0);
        Map<Integer, String> outputsByNumber = new LinkedHashMap<>();
        if (numbered) {
            for (int[] item : outputIndexes) {
                if (item[1] > 0) {
                    outputsByNumber.putIfAbsent(item[1], fences.get(item[0]).content());
                }
            }
            inputIndexes.sort((left, right) -> Integer.compare(left[1], right[1]));
        }

        List<ProblemCommon.Example> examples = new ArrayList<>();
        for (int i = 0; i < inputIndexes.size(); i++) {
            int[] item = inputIndexes.get(i);
            String ans;
            if (numbered) {
                ans = outputsByNumber.getOrDefault(item[1], "");
            } else {
                ans = i < outputIndexes.size() ? fences.get(outputIndexes.get(i)[0]).content() : "";
            }
            examples.add(newExample(fences.get(item[0]).content(), ans));
        }
        return examples;
    }

    private ProblemCommon.Example newExample(String in, String ans) {
        ProblemCommon.Example example = new ProblemCommon.Example();
        example.setIn(in);
        example.setAns(ans);
        example.setDescription("");
        return example;
    }

    private int parseIndex(String raw) {
        return StringUtils.hasText(raw) ? Integer.parseInt(raw) : 0;
    }

    /**
     * 按一级、二级标题切分章节；围栏代码块内部的 # 不参与切分
     */
    private List<Section> splitSections(String text) {
        List<Section> sections = new ArrayList<>();
        String activeFence = null;
        String title = null;
        StringBuilder body = new StringBuilder();

        for (String line : text.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            if (activeFence != null) {
                if (fenceMatcher.matches() && isClosingFence(activeFence, fenceMatcher)) {
                    activeFence = null;
                }
                body.append(line).append('\n');
                continue;
            }
            if (fenceMatcher.matches()) {
                activeFence = fenceMatcher.group(1);
                body.append(line).append('\n');
                continue;
            }
            Matcher headingMatcher = HEADING.matcher(line);
            if (headingMatcher.matches()) {
                sections.add(new Section(title, body.toString()));
                body.setLength(0);
                title = headingMatcher.group(2).trim();
                continue;
            }
            body.append(line).append('\n');
        }
        sections.add(new Section(title, body.toString()));
        return sections;
    }

    private boolean isClosingFence(String activeFence, Matcher fenceMatcher) {
        String marker = fenceMatcher.group(1);
        return marker.charAt(0) == activeFence.charAt(0)
                && marker.length() >= activeFence.length()
                && fenceMatcher.group(2).isBlank();
    }

    /**
     * 抽出一段文本里的全部围栏代码块，保留 info string（如 input1）与块内原文
     */
    private List<Fence> extractFences(String text) {
        List<Fence> fences = new ArrayList<>();
        String activeFence = null;
        String info = null;
        StringBuilder content = new StringBuilder();

        for (String line : text.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            if (activeFence == null) {
                if (fenceMatcher.matches()) {
                    activeFence = fenceMatcher.group(1);
                    info = fenceMatcher.group(2);
                    content.setLength(0);
                }
                continue;
            }
            if (fenceMatcher.matches() && isClosingFence(activeFence, fenceMatcher)) {
                fences.add(new Fence(info, trimBlock(content.toString())));
                activeFence = null;
                continue;
            }
            content.append(line).append('\n');
        }
        if (activeFence != null) {
            fences.add(new Fence(info, trimBlock(content.toString())));
        }
        return fences;
    }

    /**
     * 去掉一段文本里的全部围栏代码块，剩下的正文用于当样例解释
     */
    private String stripFences(String text) {
        StringBuilder rest = new StringBuilder();
        String activeFence = null;
        for (String line : text.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            if (activeFence == null) {
                if (fenceMatcher.matches()) {
                    activeFence = fenceMatcher.group(1);
                    continue;
                }
                rest.append(line).append('\n');
                continue;
            }
            if (fenceMatcher.matches() && isClosingFence(activeFence, fenceMatcher)) {
                activeFence = null;
            }
        }
        return rest.toString().strip();
    }

    /**
     * 样例内容只去掉首尾空行，块内的空格与缩进必须原样保留
     */
    private String trimBlock(String content) {
        String text = content;
        while (text.startsWith("\n")) {
            text = text.substring(1);
        }
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            end--;
        }
        return text.substring(0, end);
    }

    private String normalize(String markdown) {
        if (markdown == null) {
            return "";
        }
        String text = markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        return text;
    }

    /**
     * Hydro 与 judgend 的题面都允许带 YAML frontmatter，这里整段丢掉
     */
    private String stripFrontMatter(String text) {
        String[] lines = text.split("\n", -1);
        int first = 0;
        while (first < lines.length && lines[first].isBlank()) {
            first++;
        }
        if (first >= lines.length || !"---".equals(lines[first].trim())) {
            return text;
        }
        for (int i = first + 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if ("---".equals(trimmed) || "...".equals(trimmed)) {
                return String.join("\n", List.of(lines).subList(i + 1, lines.length));
            }
        }
        return text;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceAll("[\\s\\u3000]+", "").replaceAll("[：:。.]+$", "");
    }

    private void append(StringBuilder target, String body) {
        if (!StringUtils.hasText(body)) {
            return;
        }
        if (target.length() > 0) {
            target.append("\n\n");
        }
        target.append(body.strip());
    }

    private String joinBlocks(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second == null ? "" : second.strip();
        }
        if (!StringUtils.hasText(second)) {
            return first.strip();
        }
        return first.strip() + "\n\n" + second.strip();
    }

    /** 题面中的一个章节，title 为 null 表示首个标题之前的正文 */
    private record Section(String title, String body) {
    }

    /** 一个围栏代码块，info 是围栏后面那段标注 */
    private record Fence(String info, String content) {
    }
}

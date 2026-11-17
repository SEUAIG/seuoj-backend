package com.seuoj.seuojbackend.pack;

import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.model.ProblemCommon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * 题目导出：把 SEUOJ 的题面与评测配置写成 Hydro 目录结构，是导入（{@link HydroStatementParser}、
 * {@link HydroJudgeConfigConverter}）的逆过程。导出的包要能原样导回 SEUOJ。
 *
 * <p>Hydro 表达不了的三项用 Hydro 已有或忽略的字段承载：逐点分数写 cases[].score，
 * grader 写 user_extra_files，代码长度上限写扩展键 max_code_length。</p>
 */
@Component
public class HydroPackWriter {

    /** 正文里的一、二级标题会被导入器当成章节边界，导出时降成三级 */
    private static final Pattern TOP_HEADING = Pattern.compile("^( {0,3})#{1,2}(\\s+.*)$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}([`]{3,}|[~]{3,})\\s*(.*)$");
    private static final Pattern BACKTICK_RUN = Pattern.compile("`+");
    /** 只有这几种题面类型无法从评测配置推出来，需要写进 problem.yaml */
    private static final Set<String> EXPORTED_STATEMENT_TYPES =
            Set.of("Interactive", "InteractiveWithInput", "Communication");

    public String manifest(String title, List<String> tags, String statementType) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", title);
        root.put("tag", tags == null ? List.of() : tags);
        if (statementType != null && EXPORTED_STATEMENT_TYPES.contains(statementType)) {
            root.put("statement_type", statementType);
        }
        return dump(root);
    }

    public String statement(String description, String input, String output,
                            List<ProblemCommon.Example> examples, String hint) {
        StringBuilder md = new StringBuilder();
        section(md, "题目描述", description);
        section(md, "输入格式", input);
        section(md, "输出格式", output);

        List<ProblemCommon.Example> list = examples == null ? List.of() : examples;
        if (!list.isEmpty()) {
            md.append("## 样例\n\n");
            for (int i = 0; i < list.size(); i++) {
                fence(md, "input" + (i + 1), list.get(i).getIn());
                fence(md, "output" + (i + 1), list.get(i).getAns());
            }
            // 解释单独成节：放在「## 样例」里的话，解释中的代码块会被导入器当成样例
            for (int i = 0; i < list.size(); i++) {
                section(md, "样例 " + (i + 1) + " 解释", list.get(i).getDescription());
            }
        }
        section(md, "数据范围与提示", hint);
        return md.toString();
    }

    public String config(ProblemConfigDTO config) {
        ProblemConfigDTO.ProblemInfo info = config.getProblemInfo() == null
                ? new ProblemConfigDTO.ProblemInfo() : config.getProblemInfo();
        ProblemConfigDTO.CustomModules modules = config.getCustomModules() == null
                ? new ProblemConfigDTO.CustomModules() : config.getCustomModules();

        Map<String, Object> root = new LinkedHashMap<>();
        if ("Interactor".equalsIgnoreCase(info.getCheckerType())
                || "Interactive".equalsIgnoreCase(info.getProblemType())) {
            root.put("type", "interactive");
            putIfText(root, "interactor", modules.getInteractorPath());
        } else {
            root.put("type", "default");
            if ("Special".equalsIgnoreCase(info.getCheckerType()) && StringUtils.hasText(modules.getCheckerPath())) {
                root.put("checker_type", "testlib");
                root.put("checker", modules.getCheckerPath());
            }
        }
        if (info.getTimeLimitMs() != null) {
            root.put("time", info.getTimeLimitMs() + "ms");
        }
        if (info.getMemoryLimitKb() != null) {
            root.put("memory", memory(info.getMemoryLimitKb()));
        }
        if (info.getMaxCodeLength() != null) {
            root.put("max_code_length", info.getMaxCodeLength());
        }
        if (modules.getGraderPaths() != null && !modules.getGraderPaths().isEmpty()) {
            root.put("user_extra_files", modules.getGraderPaths());
        }

        List<ProblemConfigDTO.Testcase> testcases = config.getTestcases() == null ? List.of() : config.getTestcases();
        List<ProblemConfigDTO.Subtask> subtasks = config.getSubtasks() == null ? List.of() : config.getSubtasks();
        boolean scoredByCase = subtasks.isEmpty();

        // 顶层 cases 始终显式列出：导入时测试点按这个顺序编号，子任务按 input 引用
        List<Map<String, Object>> cases = new ArrayList<>();
        Map<Integer, ProblemConfigDTO.Testcase> byId = new HashMap<>();
        for (ProblemConfigDTO.Testcase testcase : testcases) {
            byId.put(testcase.getId(), testcase);
            Map<String, Object> item = caseRef(testcase);
            if (scoredByCase && testcase.getWeight() != null) {
                item.put("score", testcase.getWeight());
            }
            if (testcase.getTimeLimitMs() != null) {
                item.put("time", testcase.getTimeLimitMs() + "ms");
            }
            if (testcase.getMemoryLimitKb() != null) {
                item.put("memory", memory(testcase.getMemoryLimitKb()));
            }
            cases.add(item);
        }
        root.put("cases", cases);

        if (!subtasks.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (ProblemConfigDTO.Subtask subtask : subtasks) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", subtask.getId());
                item.put("score", subtask.getScore());
                item.put("type", subtask.getType() == null ? "min" : subtask.getType());
                if (subtask.getPreSubtasks() != null && !subtask.getPreSubtasks().isEmpty()) {
                    item.put("if", subtask.getPreSubtasks());
                }
                List<Map<String, Object>> refs = new ArrayList<>();
                for (Integer caseId : subtask.getCases() == null ? List.<Integer>of() : subtask.getCases()) {
                    ProblemConfigDTO.Testcase testcase = byId.get(caseId);
                    if (testcase != null) {
                        refs.add(caseRef(testcase));
                    }
                }
                item.put("cases", refs);
                out.add(item);
            }
            root.put("subtasks", out);
        }
        return dump(root);
    }

    /**
     * 评测端在写配置时把 checker / interactor 编译到 data 目录下的同名无后缀文件，
     * 导出时要跳过这些二进制；被测试点或 grader 引用的同名文件除外。
     */
    public Set<String> compiledArtifacts(ProblemConfigDTO config) {
        Set<String> artifacts = new LinkedHashSet<>();
        ProblemConfigDTO.CustomModules modules = config.getCustomModules();
        if (modules == null) {
            return artifacts;
        }
        for (String source : new String[]{modules.getCheckerPath(), modules.getInteractorPath()}) {
            if (!StringUtils.hasText(source)) {
                continue;
            }
            int slash = source.lastIndexOf('/');
            int dot = source.lastIndexOf('.');
            if (dot > slash + 1) {
                artifacts.add(source.substring(0, dot));
            }
        }
        if (config.getTestcases() != null) {
            for (ProblemConfigDTO.Testcase testcase : config.getTestcases()) {
                artifacts.remove(testcase.getInPath());
                artifacts.remove(testcase.getAnsPath());
            }
        }
        if (modules.getGraderPaths() != null) {
            modules.getGraderPaths().forEach(artifacts::remove);
        }
        return artifacts;
    }

    private Map<String, Object> caseRef(ProblemConfigDTO.Testcase testcase) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("input", testcase.getInPath());
        if (StringUtils.hasText(testcase.getAnsPath())) {
            item.put("output", testcase.getAnsPath());
        }
        return item;
    }

    private static String memory(long kb) {
        return kb % 1024 == 0 ? (kb / 1024) + "m" : kb + "k";
    }

    private static void putIfText(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }

    private static void section(StringBuilder md, String title, String body) {
        if (!StringUtils.hasText(body)) {
            return;
        }
        md.append("## ").append(title).append("\n\n").append(demoteHeadings(body.strip())).append("\n\n");
    }

    private static void fence(StringBuilder md, String info, String content) {
        String text = content == null ? "" : content.replace("\r\n", "\n");
        int longest = 2;
        Matcher matcher = BACKTICK_RUN.matcher(text);
        while (matcher.find()) {
            longest = Math.max(longest, matcher.group().length());
        }
        String marker = "`".repeat(longest + 1);
        md.append(marker).append(info).append('\n');
        if (!text.isEmpty()) {
            md.append(text);
            if (!text.endsWith("\n")) {
                md.append('\n');
            }
        }
        md.append(marker).append("\n\n");
    }

    /** 代码块之外的 # / ## 标题降成 ###，否则导回时会被切成新章节 */
    static String demoteHeadings(String text) {
        StringBuilder out = new StringBuilder();
        String activeFence = null;
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher fence = FENCE.matcher(line);
            if (activeFence != null) {
                if (fence.matches() && fence.group(1).charAt(0) == activeFence.charAt(0)
                        && fence.group(1).length() >= activeFence.length() && fence.group(2).isBlank()) {
                    activeFence = null;
                }
            } else if (fence.matches()) {
                activeFence = fence.group(1);
            } else {
                Matcher heading = TOP_HEADING.matcher(line);
                if (heading.matches()) {
                    line = heading.group(1) + "###" + heading.group(2);
                }
            }
            out.append(line);
            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setAllowUnicode(true);
        options.setIndent(2);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        return new Yaml(options).dump(root);
    }
}

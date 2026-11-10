package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.archive.ProblemArchiveExtractor;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.dto.problem.ProblemCreateDTO;
import com.seuoj.seuojbackend.entity.Tag;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.pack.HydroJudgeConfigConverter;
import com.seuoj.seuojbackend.pack.HydroStatement;
import com.seuoj.seuojbackend.pack.HydroStatementParser;
import com.seuoj.seuojbackend.pack.HydroYaml;
import com.seuoj.seuojbackend.vo.problem.ProblemImportVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 题目包导入服务
 *
 * <p>题目包采用 Hydro 目录结构（即 createhydro 的产物）：</p>
 * <ul>
 *     <li>problem.yaml —— 题目元信息，取 title 与 tag</li>
 *     <li>problem_zh.md —— 题面 Markdown，按章节标题拆成描述 / 输入 / 输出 / 样例 / 数据范围</li>
 *     <li>testdata/ —— 测试数据、config.yaml、checker 或 interactor 源码</li>
 * </ul>
 *
 * <p>Hydro 的 problem.yaml 不带 pid 与公开状态，这两项由导入表单提供。</p>
 */
@Slf4j
@Service
public class ProblemImportService {

    /** problem.yaml 的候选名，按顺序取第一个存在的 */
    private static final List<String> MANIFEST_CANDIDATES = List.of("problem.yaml", "problem.yml");
    /** 题面 Markdown 的候选名，优先中文题面 */
    private static final List<String> STATEMENT_CANDIDATES =
            List.of("problem_zh.md", "problem_zh-cn.md", "problem.md", "problem_en.md");
    private static final String DATA_PREFIX = "testdata/";
    /** testdata 下的评测配置，不随测试数据上传到评测端 */
    private static final String DATA_CONFIG_NAME = "config.yaml";
    private static final Set<String> DATA_CONFIG_NAMES = Set.of("config.yaml", "config.yml");

    private static final Set<String> ROOT_ENTRIES;

    static {
        Set<String> entries = new LinkedHashSet<>(MANIFEST_CANDIDATES);
        entries.addAll(STATEMENT_CANDIDATES);
        ROOT_ENTRIES = Collections.unmodifiableSet(entries);
    }

    private final ProblemArchiveExtractor archiveExtractor;
    private final ProblemService problemService;
    private final JudgeClient judgeClient;
    private final TagMapper tagMapper;
    private final Validator validator;
    private final HydroStatementParser statementParser;
    private final HydroJudgeConfigConverter configConverter;

    public ProblemImportService(ProblemArchiveExtractor archiveExtractor, ProblemService problemService,
                                JudgeClient judgeClient, TagMapper tagMapper, Validator validator,
                                HydroStatementParser statementParser,
                                HydroJudgeConfigConverter configConverter) {
        this.archiveExtractor = archiveExtractor;
        this.problemService = problemService;
        this.judgeClient = judgeClient;
        this.tagMapper = tagMapper;
        this.validator = validator;
        this.statementParser = statementParser;
        this.configConverter = configConverter;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProblemImportVO importPack(MultipartFile file, String format, String pid, Boolean isPublic) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("题目包不能为空");
        }
        if (!StringUtils.hasText(pid)) {
            throw new BadRequestException("请填写题号");
        }

        String archiveFormat = resolveArchiveFormat(file, format);
        archiveExtractor.validateArchiveSize(file.getSize());
        archiveExtractor.validateArchiveFormat(file, archiveFormat);

        List<String> warnings = new ArrayList<>();

        Map<String, byte[]> rootEntries = archiveExtractor.readExpectedEntries(file, archiveFormat, ROOT_ENTRIES);
        Map<String, Object> manifest = readManifest(rootEntries);
        HydroStatement statement = readStatement(rootEntries, warnings);

        Map<String, byte[]> dataEntries = archiveExtractor.readEntriesUnderPrefix(file, archiveFormat, DATA_PREFIX);
        if (dataEntries.isEmpty()) {
            throw new BadRequestException("题目包缺少 " + DATA_PREFIX + " 目录");
        }
        Map<String, Object> dataConfig = readDataConfig(dataEntries, warnings);
        HydroJudgeConfigConverter.Result converted =
                configConverter.convert(dataConfig, dataEntries.keySet(), warnings);

        ProblemCreateDTO createDTO = buildCreateDTO(pid, isPublic, manifest, statement);
        Set<ConstraintViolation<ProblemCreateDTO>> violations = validator.validate(createDTO);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        String createdPid = problemService.createProblem(createDTO).getPid();
        Map<String, byte[]> uploadEntries = filterUploadEntries(dataEntries);
        try {
            judgeClient.uploadProblemData(createdPid, repackDataEntries(uploadEntries));
            judgeClient.updateProblemConfig(createdPid, converted.config());
        } catch (RuntimeException ex) {
            compensateJudgeProblem(createdPid);
            throw ex;
        }

        log.info("题目包导入完成, pid={}, dataFileCount={}, testcaseCount={}",
                createdPid, uploadEntries.size(), converted.testcaseCount());

        ProblemImportVO vo = new ProblemImportVO();
        vo.setPid(createdPid);
        vo.setDataFileCount(uploadEntries.size());
        vo.setTestcaseCount(converted.testcaseCount());
        vo.setCheckerCompiled(converted.special());
        vo.setWarnings(warnings);
        return vo;
    }

    private String resolveArchiveFormat(MultipartFile file, String format) {
        String normalized = archiveExtractor.normalizeArchiveFormat(format);
        if (normalized == null) {
            normalized = archiveExtractor.normalizeArchiveFormat(guessFormatByFileName(file));
        }
        if (normalized == null) {
            throw new BadRequestException("不支持的题目包格式");
        }
        return normalized;
    }

    private String guessFormatByFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String lowerName = fileName.trim().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".tar.gz")) {
            return "tar.gz";
        }
        int dotIndex = lowerName.lastIndexOf('.');
        return dotIndex >= 0 ? lowerName.substring(dotIndex + 1) : null;
    }

    private Map<String, Object> readManifest(Map<String, byte[]> rootEntries) {
        String entryName = firstPresent(rootEntries, MANIFEST_CANDIDATES);
        if (entryName == null) {
            throw new BadRequestException("题目包缺少 " + MANIFEST_CANDIDATES.get(0));
        }
        String text = archiveExtractor.decodeUtf8Text(rootEntries.get(entryName), entryName);
        return HydroYaml.loadMap(text, entryName);
    }

    private HydroStatement readStatement(Map<String, byte[]> rootEntries, List<String> warnings) {
        String entryName = firstPresent(rootEntries, STATEMENT_CANDIDATES);
        if (entryName == null) {
            throw new BadRequestException("题目包缺少题面文件 " + STATEMENT_CANDIDATES.get(0));
        }
        String markdown = archiveExtractor.decodeUtf8Text(rootEntries.get(entryName), entryName);
        return statementParser.parse(markdown, entryName, warnings);
    }

    private Map<String, Object> readDataConfig(Map<String, byte[]> dataEntries, List<String> warnings) {
        for (String candidate : DATA_CONFIG_NAMES) {
            byte[] content = dataEntries.get(candidate);
            if (content != null) {
                String entryName = DATA_PREFIX + candidate;
                return HydroYaml.loadMap(archiveExtractor.decodeUtf8Text(content, entryName), entryName);
            }
        }
        warnings.add("题目包缺少 " + DATA_PREFIX + DATA_CONFIG_NAME + "，已按默认时空限制与普通比对导入");
        return Collections.emptyMap();
    }

    /**
     * testdata/config.yaml 是 Hydro 侧的配置，评测端用的是转换后的 JSON，不必上传
     */
    private Map<String, byte[]> filterUploadEntries(Map<String, byte[]> dataEntries) {
        Map<String, byte[]> uploadEntries = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : dataEntries.entrySet()) {
            if (DATA_CONFIG_NAMES.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            uploadEntries.put(entry.getKey(), entry.getValue());
        }
        if (uploadEntries.isEmpty()) {
            throw new BadRequestException(DATA_PREFIX + " 目录下没有测试数据文件");
        }
        return uploadEntries;
    }

    private String firstPresent(Map<String, byte[]> entries, List<String> candidates) {
        for (String candidate : candidates) {
            if (entries.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private ProblemCreateDTO buildCreateDTO(String pid, Boolean isPublic,
                                            Map<String, Object> manifest, HydroStatement statement) {
        String title = HydroYaml.str(manifest, "title");
        if (title == null) {
            throw new BadRequestException(MANIFEST_CANDIDATES.get(0) + " 缺少 title");
        }

        List<String> tagNames = HydroYaml.strList(manifest, "tag");
        if (tagNames.isEmpty()) {
            tagNames = HydroYaml.strList(manifest, "tags");
        }

        ProblemCreateDTO dto = new ProblemCreateDTO();
        dto.setPid(pid.trim().toUpperCase(Locale.ROOT));
        dto.setTitle(title);
        dto.setIsPublic(isPublic != null && isPublic);
        dto.setTags(resolveTagIds(tagNames));
        dto.setDescription(statement.description());
        dto.setInput(statement.input());
        dto.setOutput(statement.output());
        dto.setExample(statement.examples());
        dto.setHint(statement.hint());
        return dto;
    }

    /**
     * 按标签名解析标签 id，题目包中不存在的标签名只记录日志并跳过，不影响导入
     */
    private List<Long> resolveTagIds(List<String> tagNames) {
        if (tagNames == null) {
            return null;
        }

        Set<String> names = new LinkedHashSet<>();
        for (String tagName : tagNames) {
            if (StringUtils.hasText(tagName)) {
                names.add(tagName.trim());
            }
        }
        if (names.isEmpty()) {
            return null;
        }

        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .in(Tag::getTagName, names));
        List<Long> tagIds = new ArrayList<>(tags.size());
        Set<String> matchedNames = new LinkedHashSet<>();
        for (Tag tag : tags) {
            tagIds.add(tag.getId());
            matchedNames.add(tag.getTagName());
        }
        names.removeAll(matchedNames);
        if (!names.isEmpty()) {
            log.warn("题目包中存在未注册的标签, 已跳过, tagNames={}", names);
        }
        return tagIds;
    }

    /**
     * 把题目包 testdata 目录重新打成 zip，使压缩包根目录即评测端的 data 目录
     */
    private byte[] repackDataEntries(Map<String, byte[]> dataEntries) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, byte[]> entry : dataEntries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }
        } catch (IOException ex) {
            log.error("重新打包题目测试数据失败", ex);
            throw new InternalServerException("重新打包题目测试数据失败");
        }
        return outputStream.toByteArray();
    }

    /**
     * 评测端状态无法随数据库事务回滚，导入失败时显式删除已创建的题目
     */
    private void compensateJudgeProblem(String pid) {
        try {
            judgeClient.deleteProblem(pid);
        } catch (RuntimeException ex) {
            log.error("题目包导入失败后清理评测端题目失败, pid={}", pid, ex);
        }
    }
}

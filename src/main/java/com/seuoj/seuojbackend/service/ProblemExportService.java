package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.pack.HydroPackWriter;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * 题目导出为题目包（Hydro 目录结构），格式与 {@link ProblemImportService} 读取的一致，导出后可以原样导回。
 */
@Slf4j
@Service
public class ProblemExportService {

    private static final String DATA_PREFIX = "testdata/";
    private static final Set<String> RESERVED_DATA_NAMES = Set.of("config.yaml", "config.yml");

    private final ProblemService problemService;
    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;
    private final HydroPackWriter writer;

    public ProblemExportService(ProblemService problemService, ProblemMapper problemMapper,
                                JudgeClient judgeClient, HydroPackWriter writer) {
        this.problemService = problemService;
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
        this.writer = writer;
    }

    /** 题面、配置都拿到之后才开始写响应；写出第一个字节后就没法再返回 JSON 错误了 */
    public Pack prepare(String pid) {
        problemService.assertCanManageProblem(pid);
        ProblemDetailVO detail = problemMapper.getProblemDetail(pid);
        if (detail == null) {
            throw new NotFoundException("题目不存在");
        }
        List<String> tags = problemMapper.getProblemTags(pid);
        ProblemContentDTO content = judgeClient.fetchProblemContent(pid);
        ProblemConfigDTO config = judgeClient.fetchProblemConfig(pid);

        return new Pack(detail.getPid(),
                writer.manifest(detail.getTitle(), tags, content.getStatementType()),
                writer.statement(content.getDescription(), content.getInput(), content.getOutput(),
                        content.getExample(), content.getHint()),
                writer.config(config),
                writer.compiledArtifacts(config));
    }

    public void write(Pack pack, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        putText(zip, "problem.yaml", pack.manifest());
        putText(zip, "problem_zh.md", pack.statement());
        putText(zip, DATA_PREFIX + "config.yaml", pack.config());

        judgeClient.readProblemDataZip(pack.pid(), in -> copyDataEntries(pack, in, zip));
        zip.finish();
        zip.flush();
    }

    private void copyDataEntries(Pack pack, InputStream in, ZipOutputStream zip) throws IOException {
        ZipInputStream data = new ZipInputStream(in, StandardCharsets.UTF_8);
        int copied = 0;
        for (ZipEntry entry; (entry = data.getNextEntry()) != null; ) {
            String name = entry.getName().replace('\\', '/');
            if (entry.isDirectory() || !safeName(name)) {
                continue;
            }
            if (pack.compiledArtifacts().contains(name)) {
                continue;
            }
            if (RESERVED_DATA_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                // 会和导出的 testdata/config.yaml 撞名；导入时本来也不会上传这个文件
                log.warn("导出题目包时跳过与配置撞名的数据文件, pid={}, file={}", pack.pid(), name);
                continue;
            }
            zip.putNextEntry(new ZipEntry(DATA_PREFIX + name));
            StreamUtils.copy(data, zip);
            zip.closeEntry();
            copied++;
        }
        log.info("题目包导出完成, pid={}, dataFileCount={}", pack.pid(), copied);
    }

    private static boolean safeName(String name) {
        if (name.isEmpty() || name.startsWith("/")) {
            return false;
        }
        for (String part : name.split("/")) {
            if (part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public record Pack(String pid, String manifest, String statement, String config, Set<String> compiledArtifacts) {
    }
}

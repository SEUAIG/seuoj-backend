package com.seuoj.seuojbackend.archive;

import com.seuoj.seuojbackend.config.ProblemTestcaseProperties;
import com.seuoj.seuojbackend.exception.BadRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class ProblemArchiveExtractor {

    private final ProblemTestcaseProperties testcaseProperties;
    private final ArchiveFormatDetector formatDetector;
    private final ArchiveEntryReader entryReader;

    public ProblemArchiveExtractor(ProblemTestcaseProperties testcaseProperties) {
        this.testcaseProperties = testcaseProperties;
        this.formatDetector = new ArchiveFormatDetector();
        this.entryReader = new ArchiveEntryReader(testcaseProperties);
    }

    public String normalizeArchiveFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return null;
        }
        String normalized = format.trim().toLowerCase();
        return switch (normalized) {
            case "zip", "tar", "tar.gz", "tgz", "7z" -> normalized;
            default -> null;
        };
    }

    public void validateArchiveSize(long archiveSize) {
        if (archiveSize > testcaseProperties.getMaxArchiveSize()) {
            throw new BadRequestException("测试数据压缩包过大");
        }
    }

    public void validateArchiveFormat(MultipartFile file, String format) {
        String detected = formatDetector.detectArchiveFormat(file);
        String fileName = getOriginalName(file);
        switch (format) {
            case "zip", "7z" -> ensureDetected(format, detected, fileName);
            case "tar" -> {
                if ("zip".equals(detected) || "7z".equals(detected) || "gzip".equals(detected)) {
                    logAndThrowFormatMismatch(format, detected, fileName);
                }
                if (!formatDetector.looksLikeTar(file)) {
                    logAndThrowFormatMismatch(format, detected, fileName);
                }
            }
            case "tar.gz", "tgz" -> ensureDetected(format, detected, fileName, "gzip");
            default -> logAndThrowFormatMismatch(format, detected, fileName);
        }
    }

    public List<NameRuleItem> parseNameRule(String nameRule) {
        if (!StringUtils.hasText(nameRule)) {
            throw new BadRequestException("测试数据命名规则不能为空");
        }

        String[] lines = nameRule.split("\\r?\\n");
        List<NameRuleItem> rules = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        int maxRuleCount = testcaseProperties.getMaxEntryCount();

        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("\\s+");
            if (parts.length < 3) {
                throw new BadRequestException("测试数据命名规则第 " + lineNo + " 行格式错误");
            }
            if (rules.size() >= maxRuleCount) {
                throw new BadRequestException("测试数据命名规则超出上限");
            }

            int id;
            try {
                id = Integer.parseInt(parts[0]);
            } catch (NumberFormatException ex) {
                throw new BadRequestException("测试数据命名规则第 " + lineNo + " 行 id 非法");
            }

            String inputName = normalizeZipName(parts[1], "测试数据命名规则第 " + lineNo + " 行输入文件名非法");
            String answerName = normalizeZipName(parts[2], "测试数据命名规则第 " + lineNo + " 行输出文件名非法");

            if (inputName.equals(answerName)) {
                throw new BadRequestException("测试数据命名规则第 " + lineNo + " 行输入输出文件名不能相同");
            }

            if (!ids.add(id)) {
                throw new BadRequestException("测试数据命名规则存在重复 id: " + id);
            }

            if (!usedNames.add(inputName) || !usedNames.add(answerName)) {
                throw new BadRequestException("测试数据命名规则存在重复文件名: " + inputName + " / " + answerName);
            }

            rules.add(new NameRuleItem(id, inputName, answerName));
        }

        if (rules.isEmpty()) {
            throw new BadRequestException("测试数据命名规则不能为空");
        }
        log.info("测试数据命名规则解析完成, total={}, rawLines={}", rules.size(), lines.length);
        return rules;
    }

    public String decodeUtf8Text(byte[] content, String entryName) {
        return entryReader.decodeUtf8Text(content, entryName);
    }

    public java.util.Map<String, byte[]> readExpectedEntries(MultipartFile file, String format, Set<String> expectedNames) {
        return entryReader.readExpectedEntries(file, format, expectedNames);
    }

    private String normalizeZipName(String name, String errorMessage) {
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException(errorMessage);
        }
        String trimmed = name.trim();
        if (!isSafeFileName(trimmed)) {
            throw new BadRequestException(errorMessage);
        }
        return trimmed;
    }

    private boolean isSafeFileName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return false;
        }
        if (name.length() > 255) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '-' || ch == '_') {
                continue;
            }
            return false;
        }
        return true;
    }

    private void ensureDetected(String expect, String detected, String fileName) {
        if (!expect.equals(detected)) {
            logAndThrowFormatMismatch(expect, detected, fileName);
        }
    }

    private void ensureDetected(String expect, String detected, String fileName, String requiredDetected) {
        if (!requiredDetected.equals(detected)) {
            logAndThrowFormatMismatch(expect, detected, fileName);
        }
    }

    private void logAndThrowFormatMismatch(String expect, String detected, String fileName) {
        log.info("压缩包格式不匹配, expect={}, detected={}, fileName={}", expect, detected, fileName);
        throw new BadRequestException("文件格式与内容不匹配");
    }

    private String getOriginalName(MultipartFile file) {
        if (file == null || !StringUtils.hasText(file.getOriginalFilename())) {
            return "unknown";
        }
        return file.getOriginalFilename();
    }

    @Getter
    public static class NameRuleItem {
        private final int id;
        private final String inputName;
        private final String answerName;

        private NameRuleItem(int id, String inputName, String answerName) {
            this.id = id;
            this.inputName = inputName;
            this.answerName = answerName;
        }
    }
}

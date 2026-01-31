package com.seuoj.seuojbackend.util;

import com.seuoj.seuojbackend.config.ProblemTestcaseProperties;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class ProblemArchiveExtractor {

    private final ProblemTestcaseProperties testcaseProperties;

    public ProblemArchiveExtractor(ProblemTestcaseProperties testcaseProperties) {
        this.testcaseProperties = testcaseProperties;
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
        String detected = detectArchiveFormat(file);
        if ("zip".equals(format)) {
            if (!"zip".equals(detected)) {
                throw new BadRequestException("文件格式与内容不匹配");
            }
            return;
        }
        if ("7z".equals(format)) {
            if (!"7z".equals(detected)) {
                throw new BadRequestException("文件格式与内容不匹配");
            }
            return;
        }
        if ("tar".equals(format)) {
            if ("zip".equals(detected) || "7z".equals(detected) || "gzip".equals(detected)) {
                throw new BadRequestException("文件格式与内容不匹配");
            }
            return;
        }
        if ("tar.gz".equals(format) || "tgz".equals(format)) {
            if (!"gzip".equals(detected)) {
                throw new BadRequestException("文件格式与内容不匹配");
            }
            if (!looksLikeTarGz(file)) {
                throw new BadRequestException("文件格式与内容不匹配");
            }
        }
    }

    public Map<String, byte[]> readArchiveEntries(MultipartFile file, String format) {
        return switch (format) {
            case "zip" -> readZipEntries(file);
            case "tar" -> readTarEntries(file);
            case "tar.gz", "tgz" -> readTarGzEntries(file);
            case "7z" -> readSevenZEntries(file);
            default -> throw new BadRequestException("不支持的文件格式");
        };
    }

    /**
     * 解析测试数据命名规则
     */
    public List<NameRuleItem> parseNameRule(String nameRule) {
        if (!StringUtils.hasText(nameRule)) {
            throw new BadRequestException("测试数据命名规则不能为空");
        }

        String[] lines = nameRule.split("\\r?\\n");
        List<NameRuleItem> rules = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        Set<String> usedNames = new HashSet<>();

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

    /**
     * 读取 zip 压缩包内容
     */
    private Map<String, byte[]> readZipEntries(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> entryNames = new ArrayList<>();
        ArchiveReadState state = new ArchiveReadState(testcaseProperties, file.getSize());
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = normalizeZipEntryName(entry.getName(), "压缩包包含非法文件名");
                putEntry(entries, entryNames, state, entryName,
                        () -> readAllBytes(zipInputStream, entryName, state));
            }
        } catch (IOException ex) {
            log.error("读取测试数据压缩包失败", ex);
            throw new InternalServerException("读取测试数据压缩包失败");
        }

        return finalizeEntries(entries, entryNames);
    }

    /**
     * 读取 tar 压缩包内容
     */
    private Map<String, byte[]> readTarEntries(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> entryNames = new ArrayList<>();
        ArchiveReadState state = new ArchiveReadState(testcaseProperties, file.getSize());
        try (TarArchiveInputStream tarInputStream = new TarArchiveInputStream(file.getInputStream())) {
            readTarStreamEntries(tarInputStream, entries, entryNames, state);
        } catch (IOException ex) {
            log.error("读取测试数据压缩包失败", ex);
            throw new InternalServerException("读取测试数据压缩包失败");
        }

        return finalizeEntries(entries, entryNames);
    }

    /**
     * 读取 tar.gz / tgz 压缩包内容
     */
    private Map<String, byte[]> readTarGzEntries(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> entryNames = new ArrayList<>();
        ArchiveReadState state = new ArchiveReadState(testcaseProperties, file.getSize());
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(file.getInputStream());
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {
            readTarStreamEntries(tarInputStream, entries, entryNames, state);
        } catch (IOException ex) {
            log.error("读取测试数据压缩包失败", ex);
            throw new InternalServerException("读取测试数据压缩包失败");
        }

        return finalizeEntries(entries, entryNames);
    }

    /**
     * 统一处理 tar 流的条目读取逻辑
     */
    private void readTarStreamEntries(TarArchiveInputStream tarInputStream,
                                      Map<String, byte[]> entries,
                                      List<String> entryNames,
                                      ArchiveReadState state) throws IOException {
        while (true) {
            var entry = tarInputStream.getNextEntry();
            if (entry == null) {
                break;
            }
            if (entry.isDirectory()) {
                continue;
            }
            if (entry.isSymbolicLink() || entry.isLink()) {
                throw new BadRequestException("压缩包包含非法链接文件");
            }
            String entryName = normalizeZipEntryName(entry.getName(), "压缩包包含非法文件名");
            putEntry(entries, entryNames, state, entryName,
                    () -> readAllBytes(tarInputStream, entryName, state));
        }
    }

    /**
     * 读取 7z 压缩包内容
     */
    private Map<String, byte[]> readSevenZEntries(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> entryNames = new ArrayList<>();
        ArchiveReadState state = new ArchiveReadState(testcaseProperties, file.getSize());
        File tempFile = null;
        try {
            tempFile = File.createTempFile("seuoj-testcase-", ".7z");
            file.transferTo(tempFile);
            try (SevenZFile sevenZFile = SevenZFile.builder().setFile(tempFile).get()) {
                SevenZArchiveEntry entry;
                while ((entry = sevenZFile.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (!entry.hasStream()) {
                        continue;
                    }
                    String entryName = normalizeZipEntryName(entry.getName(), "压缩包包含非法文件名");
                    putEntry(entries, entryNames, state, entryName,
                            () -> readAllBytes(sevenZFile, entryName, state));
                }
            }
        } catch (IOException ex) {
            log.error("读取测试数据压缩包失败", ex);
            throw new InternalServerException("读取测试数据压缩包失败");
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
        }

        return finalizeEntries(entries, entryNames);
    }

    private byte[] readAllBytes(InputStream inputStream, String entryName, ArchiveReadState state) throws IOException {
        return readAllBytesInternal(inputStream::read, entryName, state);
    }

    private byte[] readAllBytes(SevenZFile sevenZFile, String entryName, ArchiveReadState state) throws IOException {
        return readAllBytesInternal(sevenZFile::read, entryName, state);
    }

    private byte[] readAllBytesInternal(BufferReader reader, String entryName, ArchiveReadState state) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long entrySize = 0;
        int len;
        while ((len = reader.read(buffer)) > 0) {
            entrySize += len;
            if (entrySize > state.getMaxEntrySize()) {
                throw new BadRequestException("解压后单文件过大: " + entryName);
            }
            state.addBytes(len);
            outputStream.write(buffer, 0, len);
        }
        return outputStream.toByteArray();
    }

    /**
     * 统一处理条目去重、计数与读取
     */
    private void putEntry(Map<String, byte[]> entries,
                          List<String> entryNames,
                          ArchiveReadState state,
                          String entryName,
                          EntryReader reader) throws IOException {
        validateEntryCount(state);
        if (entries.containsKey(entryName)) {
            throw new BadRequestException("压缩包存在重复文件名: " + entryName);
        }
        byte[] content = reader.read();
        entryNames.add(entryName);
        entries.put(entryName, content);
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

    private String normalizeZipEntryName(String name, String errorMessage) {
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException(errorMessage);
        }
        String normalized = name.trim().replace("\\", "/");
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new BadRequestException(errorMessage);
        }
        String[] segments = normalized.split("/");
        if (segments.length == 0) {
            throw new BadRequestException(errorMessage);
        }
        for (String segment : segments) {
            if (segment.isEmpty() || "..".equals(segment)) {
                throw new BadRequestException(errorMessage);
            }
            if (!isSafeFileName(segment)) {
                throw new BadRequestException(errorMessage);
            }
        }
        return normalized;
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

    private void validateEntryCount(ArchiveReadState state) {
        state.incrementEntryCount();
        if (state.getEntryCount() > testcaseProperties.getMaxEntryCount()) {
            throw new BadRequestException("压缩包文件数量过多");
        }
    }

    private Map<String, byte[]> finalizeEntries(Map<String, byte[]> entries, List<String> entryNames) {
        if (entries.isEmpty()) {
            throw new BadRequestException("压缩包中未找到有效文件");
        }

        String rootDir = detectSingleRootDir(entryNames);
        if (rootDir != null) {
            log.info("检测到压缩包顶层目录, rootDir={}", rootDir);
            entries = stripRootDir(entries, rootDir);
        } else {
            for (String entryName : entryNames) {
                if (entryName.contains("/")) {
                    throw new BadRequestException("压缩包目录结构不支持");
                }
            }
        }

        return entries;
    }

    private String detectSingleRootDir(List<String> entryNames) {
        String root = null;
        for (String entryName : entryNames) {
            String normalized = entryName.replace("\\", "/");
            int slashIndex = normalized.indexOf('/');
            if (slashIndex <= 0) {
                return null;
            }
            String firstSegment = normalized.substring(0, slashIndex);
            if (root == null) {
                root = firstSegment;
            } else if (!root.equals(firstSegment)) {
                return null;
            }
        }
        return root;
    }

    private Map<String, byte[]> stripRootDir(Map<String, byte[]> entries, String rootDir) {
        Map<String, byte[]> stripped = new HashMap<>();
        String prefix = rootDir + "/";
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(prefix)) {
                throw new BadRequestException("压缩包中文件目录不一致");
            }
            String strippedName = name.substring(prefix.length());
            if (!StringUtils.hasText(strippedName) || strippedName.contains("/")) {
                throw new BadRequestException("压缩包只允许一层目录嵌套");
            }
            if (!isSafeFileName(strippedName)) {
                throw new BadRequestException("压缩包包含非法文件名");
            }
            if (stripped.containsKey(strippedName)) {
                throw new BadRequestException("压缩包存在重复文件名: " + strippedName);
            }
            stripped.put(strippedName, entry.getValue());
        }
        return stripped;
    }

    private String detectArchiveFormat(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(512);
            if (header.length >= 6) {
                if ((header[0] & 0xFF) == 0x37 && (header[1] & 0xFF) == 0x7A
                        && (header[2] & 0xFF) == 0xBC && (header[3] & 0xFF) == 0xAF
                        && (header[4] & 0xFF) == 0x27 && (header[5] & 0xFF) == 0x1C) {
                    return "7z";
                }
            }
            if (header.length >= 4) {
                if (header[0] == 'P' && header[1] == 'K'
                        && (header[2] == 3 || header[2] == 5 || header[2] == 7)
                        && (header[3] == 4 || header[3] == 6 || header[3] == 8)) {
                    return "zip";
                }
            }
            if (header.length >= 2) {
                if ((header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
                    return "gzip";
                }
            }
            if (hasUstar(header)) {
                return "tar";
            }
            return "unknown";
        } catch (IOException ex) {
            log.error("读取压缩包头信息失败", ex);
            throw new InternalServerException("读取测试数据压缩包失败");
        }
    }

    private boolean looksLikeTarGz(MultipartFile file) {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(file.getInputStream())) {
            byte[] header = gzipInputStream.readNBytes(512);
            return hasUstar(header);
        } catch (IOException ex) {
            log.warn("读取tar.gz压缩包头失败", ex);
            return false;
        }
    }

    private boolean hasUstar(byte[] header) {
        if (header.length < 262) {
            return false;
        }
        return header[257] == 'u' && header[258] == 's'
                && header[259] == 't' && header[260] == 'a'
                && header[261] == 'r';
    }

    private static class ArchiveReadState {
        private final long maxEntrySize;
        private final long maxTotalSize;
        private final long compressedSize;
        private final int maxCompressionRatio;
        private int entryCount;
        private long totalUncompressedSize;

        private ArchiveReadState(ProblemTestcaseProperties properties, long compressedSize) {
            this.maxEntrySize = properties.getMaxEntrySize();
            this.maxTotalSize = properties.getMaxTotalSize();
            this.maxCompressionRatio = properties.getMaxCompressionRatio();
            this.compressedSize = Math.max(compressedSize, 0);
        }

        private void addBytes(int bytes) {
            totalUncompressedSize += bytes;
            if (totalUncompressedSize > maxTotalSize) {
                throw new BadRequestException("解压后文件总大小超限");
            }
            if (maxCompressionRatio > 0 && compressedSize > 0) {
                if (totalUncompressedSize > compressedSize * (long) maxCompressionRatio) {
                    throw new BadRequestException("压缩包压缩比过高");
                }
            }
        }

        private int getEntryCount() {
            return entryCount;
        }

        private void incrementEntryCount() {
            entryCount++;
        }

        private long getMaxEntrySize() {
            return maxEntrySize;
        }
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

    @FunctionalInterface
    private interface EntryReader {
        byte[] read() throws IOException;
    }

    @FunctionalInterface
    private interface BufferReader {
        int read(byte[] buffer) throws IOException;
    }
}

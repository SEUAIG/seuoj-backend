package com.seuoj.seuojbackend.archive;

import com.seuoj.seuojbackend.config.ProblemTestcaseProperties;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
class ArchiveEntryReader {

    private final ProblemTestcaseProperties testcaseProperties;

    ArchiveEntryReader(ProblemTestcaseProperties testcaseProperties) {
        this.testcaseProperties = testcaseProperties;
    }

    public Map<String, byte[]> readExpectedEntries(MultipartFile file, String format, Set<String> expectedNames) {
        if (expectedNames == null || expectedNames.isEmpty()) {
            throw new BadRequestException("测试数据命名规则不能为空");
        }
        return switch (format) {
            case "zip" -> readZipEntries(file, expectedNames);
            case "tar" -> readTarEntries(file, expectedNames);
            case "tar.gz", "tgz" -> readTarGzEntries(file, expectedNames);
            case "7z" -> readSevenZEntries(file, expectedNames);
            default -> throw new BadRequestException("不支持的文件格式");
        };
    }

    public String decodeUtf8Text(byte[] content, String entryName) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            String text = decoder.decode(ByteBuffer.wrap(content)).toString();
            validateTextContent(text, entryName);
            return text;
        } catch (CharacterCodingException ex) {
            throw new BadRequestException("测试数据内容必须为UTF-8: " + entryName);
        }
    }

    private Map<String, byte[]> readZipEntries(MultipartFile file, Set<String> expectedNames) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> entryNames = new ArrayList<>();
        ArchiveReadState state = new ArchiveReadState(testcaseProperties, file.getSize());
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = normalizeZipEntryName(entry.getName(), "压缩包包含非法文件名");
                boolean keep = shouldKeepEntry(entryName, expectedNames);
                validateEntryCount(state);
                byte[] content = readEntryBytes(zipInputStream::read, entryName, state, keep);
                if (keep) {
                    ensureNoDuplicate(entries, entryName);
                    entryNames.add(entryName);
                    entries.put(entryName, content);
                }
            }
        } catch (IOException ex) {
            log.error("读取测试数据压缩包失败", ex);
            throw new InternalServerException("读取测试数据压缩包失败");
        }

        return finalizeEntries(entries, entryNames);
    }

    private Map<String, byte[]> readTarEntries(MultipartFile file, Set<String> expectedNames) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> entryNames = new ArrayList<>();
        ArchiveReadState state = new ArchiveReadState(testcaseProperties, file.getSize());
        try (TarArchiveInputStream tarInputStream = createTarInputStream(file.getInputStream())) {
            readTarStreamEntries(tarInputStream, entries, entryNames, state, expectedNames);
        } catch (IOException ex) {
            log.warn("读取测试数据压缩包失败", ex);
            throw new BadRequestException("压缩包格式错误");
        }

        return finalizeEntries(entries, entryNames);
    }

    private Map<String, byte[]> readTarGzEntries(MultipartFile file, Set<String> expectedNames) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> entryNames = new ArrayList<>();
        ArchiveReadState state = new ArchiveReadState(testcaseProperties, file.getSize());
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(file.getInputStream());
             TarArchiveInputStream tarInputStream = createTarInputStream(gzipInputStream)) {
            readTarStreamEntries(tarInputStream, entries, entryNames, state, expectedNames);
        } catch (IOException ex) {
            log.warn("读取测试数据压缩包失败", ex);
            throw new BadRequestException("压缩包格式错误");
        }

        return finalizeEntries(entries, entryNames);
    }

    private void readTarStreamEntries(TarArchiveInputStream tarInputStream,
                                      Map<String, byte[]> entries,
                                      List<String> entryNames,
                                      ArchiveReadState state,
                                      Set<String> expectedNames) throws IOException {
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
            boolean keep = shouldKeepEntry(entryName, expectedNames);
            validateEntryCount(state);
            byte[] content = readEntryBytes(tarInputStream::read, entryName, state, keep);
            if (keep) {
                ensureNoDuplicate(entries, entryName);
                entryNames.add(entryName);
                entries.put(entryName, content);
            }
        }
    }

    private Map<String, byte[]> readSevenZEntries(MultipartFile file, Set<String> expectedNames) {
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
                    boolean keep = shouldKeepEntry(entryName, expectedNames);
                    validateEntryCount(state);
                    byte[] content = readEntryBytes(sevenZFile::read, entryName, state, keep);
                    if (keep) {
                        ensureNoDuplicate(entries, entryName);
                        entryNames.add(entryName);
                        entries.put(entryName, content);
                    }
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

    private byte[] readEntryBytes(BufferReader reader, String entryName, ArchiveReadState state, boolean keep)
            throws IOException {
        ByteArrayOutputStream outputStream = keep ? new ByteArrayOutputStream() : null;
        CharsetDecoder decoder = keep ? createUtf8Decoder(entryName) : null;
        CharBuffer charBuffer = keep ? CharBuffer.allocate(4096) : null;
        byte[] buffer = new byte[4096];
        long entrySize = 0;
        int len;
        while ((len = reader.read(buffer)) > 0) {
            entrySize += len;
            if (entrySize > state.getMaxEntrySize()) {
                throw new BadRequestException("解压后单文件过大: " + entryName);
            }
            state.addBytes(len);
            if (keep) {
                validateUtf8Chunk(decoder, charBuffer, buffer, len, entryName);
                outputStream.write(buffer, 0, len);
            }
        }
        if (keep) {
            finishUtf8Decode(decoder, charBuffer, entryName);
        }
        return keep ? outputStream.toByteArray() : null;
    }

    private CharsetDecoder createUtf8Decoder(String entryName) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        } catch (Exception ex) {
            throw new BadRequestException("测试数据内容必须为UTF-8: " + entryName);
        }
    }

    private void validateUtf8Chunk(CharsetDecoder decoder, CharBuffer charBuffer,
                                   byte[] buffer, int len, String entryName) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, len);
        while (byteBuffer.hasRemaining()) {
            CoderResult result = decoder.decode(byteBuffer, charBuffer, false);
            if (result.isError()) {
                try {
                    result.throwException();
                } catch (CharacterCodingException ex) {
                    throw new BadRequestException("测试数据内容必须为UTF-8: " + entryName);
                }
            }
            charBuffer.clear();
        }
    }

    private void finishUtf8Decode(CharsetDecoder decoder, CharBuffer charBuffer, String entryName) {
        try {
            CoderResult result = decoder.decode(ByteBuffer.allocate(0), charBuffer, true);
            if (result.isError()) {
                result.throwException();
            }
            result = decoder.flush(charBuffer);
            if (result.isError()) {
                result.throwException();
            }
        } catch (CharacterCodingException ex) {
            throw new BadRequestException("测试数据内容必须为UTF-8: " + entryName);
        }
    }

    private void ensureNoDuplicate(Map<String, byte[]> entries, String entryName) {
        if (entries.containsKey(entryName)) {
            throw new BadRequestException("压缩包存在重复文件名: " + entryName);
        }
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

    private boolean shouldKeepEntry(String entryName, Set<String> expectedNames) {
        if (expectedNames.contains(entryName)) {
            return true;
        }
        int slashIndex = entryName.indexOf('/');
        if (slashIndex <= 0) {
            return false;
        }
        if (entryName.indexOf('/', slashIndex + 1) >= 0) {
            return false;
        }
        String leaf = entryName.substring(slashIndex + 1);
        return expectedNames.contains(leaf);
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

    private TarArchiveInputStream createTarInputStream(InputStream inputStream) {
        return new TarArchiveInputStream(inputStream, StandardCharsets.UTF_8.name());
    }

    private void validateTextContent(String text, String entryName) {
        long maxTextSize = testcaseProperties.getMaxTextSize();
        if (maxTextSize > 0 && text.length() > maxTextSize) {
            throw new BadRequestException("测试数据文本过大: " + entryName);
        }
        int maxLineLength = testcaseProperties.getMaxLineLength();
        if (maxLineLength > 0) {
            int lineLength = 0;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\n' || ch == '\r') {
                    lineLength = 0;
                    continue;
                }
                lineLength++;
                if (lineLength > maxLineLength) {
                    throw new BadRequestException("测试数据存在过长行: " + entryName);
                }
            }
        }
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

    @FunctionalInterface
    private interface BufferReader {
        int read(byte[] buffer) throws IOException;
    }
}

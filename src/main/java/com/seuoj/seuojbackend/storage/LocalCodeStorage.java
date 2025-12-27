package com.seuoj.seuojbackend.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.seuoj.seuojbackend.exception.CodeStorageException;

/**
 * 本地代码存储实现类
 */
@Slf4j
@Component
public class LocalCodeStorage implements CodeStorage {

    @Value("${storage.user-code-storage-path}")
    private String localStoragePath;

    @Override
    public void save(String code, String submissionNo) {
        Path path = Paths.get(localStoragePath, submissionNo + ".txt");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                log.error("代码文件已存在，拒绝覆盖，可能存在重复提交号 submissionNo={}", submissionNo);
                throw new CodeStorageException("保存代码失败：重复的提交编号");
            }
            Files.writeString(path, code, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("保存代码失败 submissionNo={}", submissionNo, e);
            throw new CodeStorageException("保存代码失败", e);
        }
    }

    @Override
    public String getCode(String submissionNo) {
        Path path = Paths.get(localStoragePath, submissionNo + ".txt");
        if (!Files.exists(path)) {
            log.error("代码文件不存在 submissionNo={}", submissionNo);
            throw new CodeStorageException("代码文件不存在");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取代码失败 submissionNo={}", submissionNo, e);
            throw new CodeStorageException("读取代码失败", e);
        }
    }

    @Override
    public void delete(String submissionNo) {
        Path path = Paths.get(localStoragePath, submissionNo + ".txt");
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("删除代码失败 submissionNo={}", submissionNo, e);
            throw new CodeStorageException("删除代码失败", e);
        }
    }
}

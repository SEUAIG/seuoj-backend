package com.seuoj.seuojbackend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地代码存储实现类
 */
@Component
public class LocalCodeStorage implements CodeStorage {

    @Value("${storage.user-code-storage-path}")
    private String localStoragePath;

    @Override
    public void save(String code, String submissionNo) {
        Path path = Paths.get(localStoragePath, submissionNo + ".txt");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, code, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("保存代码失败", e);
        }
    }

    @Override
    public String getCode(String submissionNo) {
        Path path = Paths.get(localStoragePath, submissionNo + ".txt");
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取代码失败", e);
        }
    }
}

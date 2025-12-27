package com.seuoj.seuojbackend;

import com.seuoj.seuojbackend.storage.LocalCodeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单元测试，使用 @TempDir 临时目录，不依赖 Spring，不写入项目数据目录
 */
class LocalCodeStorageTest {

    @TempDir
    Path tempDir;

    private LocalCodeStorage codeStorage;

    @BeforeEach
    void setUp() throws Exception {
        codeStorage = new LocalCodeStorage();
        Field field = LocalCodeStorage.class.getDeclaredField("localStoragePath");
        field.setAccessible(true);
        field.set(codeStorage, tempDir.toString());
    }

    @Test
    void saveAndRead_shouldWorkInTempDir() throws Exception {
        codeStorage.save("hello", "abc");
        assertThat(Files.exists(tempDir.resolve("abc.txt"))).isTrue();
        assertThat(codeStorage.getCode("abc")).isEqualTo("hello");
    }

    @Test
    void delete_shouldRemoveFile() throws Exception {
        codeStorage.save("hello", "abc");
        codeStorage.delete("abc");
        assertThat(Files.exists(tempDir.resolve("abc.txt"))).isFalse();
    }
}

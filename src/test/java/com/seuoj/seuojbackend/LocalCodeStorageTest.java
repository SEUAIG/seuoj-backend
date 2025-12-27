package com.seuoj.seuojbackend;

import com.seuoj.seuojbackend.storage.CodeStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class LocalCodeStorageTest {
    @Autowired
    private CodeStorage codeStorage;

    @Test
    public void save() throws Exception {
        String code = "123123123";
        codeStorage.save(code, "123");
    }

    @Test
    public void getCode() throws Exception {
        String code = codeStorage.getCode("123");
        System.out.println(code);
    }
}

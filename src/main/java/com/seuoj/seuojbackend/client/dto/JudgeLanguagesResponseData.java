package com.seuoj.seuojbackend.client.dto;

import java.util.List;
import lombok.Data;

@Data
public class JudgeLanguagesResponseData {
    private List<LanguageItem> languages;

    @Data
    public static class LanguageItem {
        private String name;
        private Boolean available;
        private String version;
    }
}

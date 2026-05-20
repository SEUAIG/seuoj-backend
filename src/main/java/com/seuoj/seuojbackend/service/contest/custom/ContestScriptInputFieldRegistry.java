package com.seuoj.seuojbackend.service.contest.custom;

import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

@Component
public class ContestScriptInputFieldRegistry {

    private static final List<String> DEFAULT_ENABLED_FIELDS = List.of(
            "id", "language", "score", "verdict", "submit_time", "error_detail", "error_length"
    );

    private final Map<String, FieldDef> fieldDefs;

    public ContestScriptInputFieldRegistry() {
        Map<String, FieldDef> defs = new LinkedHashMap<>();
        defs.put("id", new FieldDef("id", "提交ID", "submission 主键ID", "number", 0L,
                (s, d) -> s.getId() != null ? s.getId() : 0L));
        defs.put("score", new FieldDef("score", "得分", "评测得分，缺失时为 0", "number", 0,
                (s, d) -> s.getScore() != null ? s.getScore() : 0));
        defs.put("verdict", new FieldDef("verdict", "判题结果", "如 Accepted/WA/TLE，缺失时为空字符串", "string", "",
                (s, d) -> s.getVerdict() != null ? s.getVerdict() : ""));
        defs.put("submit_time", new FieldDef("submit_time", "提交时间", "ISO-8601 时间字符串，缺失时为空字符串", "string", "",
                (s, d) -> s.getSubmitTime() != null ? s.getSubmitTime().toString() : ""));
        defs.put("language", new FieldDef("language", "编程语言", "提交使用的语言，缺失时为空字符串", "string", "",
                (s, d) -> s.getLanguage() != null ? s.getLanguage() : ""));
        defs.put("error_detail", new FieldDef("error_detail", "错误详情", "编译/运行错误详情文本，缺失时为空字符串", "string", "",
                (s, d) -> d != null && d.getErrorDetail() != null ? d.getErrorDetail() : ""));
        defs.put("error_length", new FieldDef("error_length", "错误长度", "error_detail 字符长度，缺失时为 0", "number", 0,
                (s, d) -> {
                    if (d == null || d.getErrorDetail() == null) return 0;
                    return d.getErrorDetail().length();
                }));
        this.fieldDefs = defs;
    }

    public List<String> normalizeEnabledFields(List<String> enabledFields) {
        if (enabledFields == null) {
            return List.of();
        }
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String key : enabledFields) {
            if (!fieldDefs.containsKey(key)) {
                throw new BadRequestException("非法输入字段 key: " + key);
            }
            deduplicated.add(key);
        }
        return new ArrayList<>(deduplicated);
    }

    public List<String> resolveEnabledFieldsOrDefault(List<String> enabledFields) {
        if (enabledFields == null || enabledFields.isEmpty()) {
            return DEFAULT_ENABLED_FIELDS;
        }
        return normalizeEnabledFields(enabledFields);
    }

    public List<String> resolveRequestedFields(List<String> requestedFields) {
        if (requestedFields == null || requestedFields.isEmpty()) {
            return DEFAULT_ENABLED_FIELDS;
        }
        Set<String> requestedSet = new LinkedHashSet<>();
        for (String key : requestedFields) {
            if (fieldDefs.containsKey(key)) {
                requestedSet.add(key);
            }
        }
        List<String> selected = new ArrayList<>();
        for (String key : fieldDefs.keySet()) {
            if (requestedSet.contains(key)) {
                selected.add(key);
            }
        }
        return selected.isEmpty() ? DEFAULT_ENABLED_FIELDS : selected;
    }

    public Set<String> getSystemFieldKeys() {
        return Collections.unmodifiableSet(fieldDefs.keySet());
    }

    public Map<String, Object> buildSubmissionInput(Submission submission,
                                                     SubmissionDetail detail,
                                                     List<String> enabledFields) {
        List<String> resolvedFields = resolveEnabledFieldsOrDefault(enabledFields);
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : resolvedFields) {
            FieldDef def = fieldDefs.get(key);
            Object value = def.extractor().apply(submission, detail);
            map.put(key, value != null ? value : def.defaultValue());
        }
        return map;
    }

    private record FieldDef(String key,
                            String labelZh,
                            String descriptionZh,
                            String typeHint,
                            Object defaultValue,
                            BiFunction<Submission, SubmissionDetail, Object> extractor) {
    }
}

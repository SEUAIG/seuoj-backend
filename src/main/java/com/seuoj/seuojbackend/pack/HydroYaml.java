package com.seuoj.seuojbackend.pack;

import com.seuoj.seuojbackend.exception.BadRequestException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Hydro 题目包里的 YAML 解析与取值辅助。
 * 一律走 SafeConstructor，只解析出 Map / List / 标量，不允许 YAML 反序列化成任意 Java 类型。
 */
public final class HydroYaml {

    /** 允许的锚点引用数量，防 billion laughs */
    private static final int MAX_ALIASES = 16;

    /** 单个 YAML 文件的字符上限，题目包里的 yaml 都很小 */
    private static final int MAX_CODE_POINTS = 2 * 1024 * 1024;

    private HydroYaml() {
    }

    /**
     * 把一个 YAML 文本解析成字符串键的 Map；空文件按空 Map 处理。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadMap(String text, String entryName) {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(MAX_ALIASES);
        loaderOptions.setCodePointLimit(MAX_CODE_POINTS);
        DumperOptions dumperOptions = new DumperOptions();

        Object loaded;
        try {
            Yaml yaml = new Yaml(new SafeConstructor(loaderOptions), new Representer(dumperOptions),
                    dumperOptions, loaderOptions);
            loaded = yaml.load(text);
        } catch (YAMLException ex) {
            throw new BadRequestException(entryName + " 不是合法的 YAML: " + rootMessage(ex));
        }

        if (loaded == null) {
            return Collections.emptyMap();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new BadRequestException(entryName + " 的顶层必须是键值对");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new BadRequestException(entryName + " 存在非字符串的键: " + key);
            }
        }
        return (Map<String, Object>) map;
    }

    /**
     * 取标量字段，去掉首尾空白；缺失或空串返回 null。
     */
    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value instanceof Map || value instanceof List) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 取字符串数组字段；单个标量按只有一个元素处理。
     */
    public static List<String> strList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item == null || item instanceof Map || item instanceof List) {
                    continue;
                }
                String text = String.valueOf(item).trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return result;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            result.add(text);
        }
        return result;
    }

    /**
     * 取「元素为键值对」的数组字段，例如 cases / subtasks。
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mapList(Map<String, Object> map, String key, String entryName) {
        Object value = map.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof List<?> list)) {
            throw new BadRequestException(entryName + " 的 " + key + " 必须是数组");
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                throw new BadRequestException(entryName + " 的 " + key + " 元素必须是键值对");
            }
            result.add((Map<String, Object>) itemMap);
        }
        return result;
    }

    /**
     * 取整数字段；缺失返回 null，非整数直接报错。
     */
    public static Integer intValue(Map<String, Object> map, String key, String context) {
        String text = str(map, key);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(context + " 的 " + key + " 必须是整数: " + text);
        }
    }

    /**
     * 取整数数组字段，例如 subtasks 的 if（前置子任务）。
     */
    public static List<Integer> intList(Map<String, Object> map, String key, String context) {
        List<String> raw = strList(map, key);
        List<Integer> result = new ArrayList<>(raw.size());
        for (String text : raw) {
            try {
                result.add(Integer.valueOf(text));
            } catch (NumberFormatException ex) {
                throw new BadRequestException(context + " 的 " + key + " 必须是整数数组: " + text);
            }
        }
        return result;
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message.replaceAll("\s+", " ").trim();
    }
}

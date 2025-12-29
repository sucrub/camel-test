package com.example.demo.util;

import com.example.demo.config.ApiConfigRegistry;
import com.jayway.jsonpath.JsonPath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Util {
    public static String resolveTemplate(String template, Map<String, String> pathVariables) {
        String result = template;
        for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static void validateHttpResponse(Integer status, String body, String target) {
        if (status == null || status >= 400) {
            throw new RuntimeException("HTTP " + status + " when calling " + target);
        }
        if (body == null || body.isBlank()) {
            throw new RuntimeException("Empty response body from " + target);
        }
    }

    // TODO: What if the result is a list, how can I DTO it
    public static Object extractByJsonPath(String body, ApiConfigRegistry.Source cfg) {

        Object root = JsonPath.read(body, cfg.getPath());

        // No DTO mapping → return raw result
        if (cfg.getFields() == null || cfg.getFields().isEmpty()) {
            return root;
        }

        // Case 1: root is a LIST → map each element
        if (root instanceof List<?> list) {
            return list.stream()
                    .map(item -> extractFields(item, cfg))
                    .toList();
        }

        // Case 2: root is an OBJECT → map once
        return extractFields(root, cfg);
    }

    private static Map<String, Object> extractFields(
            Object source,
            ApiConfigRegistry.Source cfg
    ) {
        Map<String, Object> extracted = new LinkedHashMap<>();

        cfg.getFields().forEach((field, path) -> {
            Object value = JsonPath.read(source, path);
            extracted.put(field, value);
        });

        return extracted;
    }
}

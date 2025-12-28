package com.example.demo.util;

import com.example.demo.config.ApiConfigRegistry;
import com.jayway.jsonpath.JsonPath;

import java.util.LinkedHashMap;
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

        if (cfg.getFields() == null || cfg.getFields().isEmpty()) {
            return root;
        }

        Map<String, Object> extracted = new LinkedHashMap<>();
        cfg.getFields().forEach((field, path) ->
                extracted.put(field, JsonPath.read(root, path))
        );

        return extracted;
    }
}

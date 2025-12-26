package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "api")
@Getter @Setter
public class ApiConfigRegistry {

    // Group API tập hợp nhiều bộ api khác nhau
    private Map<String, ApiGroup> groups;

    @Getter @Setter
    public static class ApiGroup {
        private Map<String, Source> sources;
    }

    // URL: đường API call
    // Path: JsonPath gốc
    // Fields: Mapping DTO
    @Getter @Setter
    public static class Source {
        private String type;
        private String soapAction;
        private String url;
        private String path;
        private Map<String, String> fields;
        private DbConfig config;
        private String query;
    }

    @Getter @Setter
    public static class DbConfig {
        private String url;
        private String username;
        private String password;
        private String driver;
    }
}

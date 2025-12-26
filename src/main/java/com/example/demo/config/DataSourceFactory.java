package com.example.demo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DataSourceFactory {

    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * Get or create a DataSource from DbConfig
     * Uses connection string as cache key to reuse datasources with same config
     */
    public DataSource getDataSource(ApiConfigRegistry.DbConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DbConfig cannot be null");
        }

        // Use connection URL as cache key to share datasources with same config
        String cacheKey = config.getUrl() + "|" + config.getUsername();
        return dataSourceCache.computeIfAbsent(cacheKey, k -> createDataSource(config));
    }

    /**
     * Create a new HikariCP DataSource from DbConfig
     */
    private DataSource createDataSource(ApiConfigRegistry.DbConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriver());

        // Configure pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setPoolName("DynamicPool-" + config.getUrl().hashCode());

        return new HikariDataSource(hikariConfig);
    }

    /**
     * Close all cached datasources
     */
    public void closeAll() {
        dataSourceCache.values().forEach(ds -> {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        });
        dataSourceCache.clear();
    }
}
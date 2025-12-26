package com.example.demo.service;

import com.example.demo.config.ApiConfigRegistry;
import com.example.demo.config.DataSourceFactory;
import com.example.demo.dto.ApiCallResult;
import com.example.demo.exception.ApiAggregationException;
import com.example.demo.template.SoapRequestBodyXml;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ApiAggregationService {

    @Autowired
    private ApiConfigRegistry apiConfigRegistry;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private Executor apiExecutor;

    @Autowired
    private DataSourceFactory dataSourceFactory;

    public Map<String, Object> aggregateApis(String groupName) {
        return aggregateApis(groupName, Map.of());
    }

    public Map<String, Object> aggregateApis(String groupName, Map<String, String> pathVariables) {

        ApiConfigRegistry.ApiGroup group = apiConfigRegistry.getGroups().get(groupName);

        if (group == null) {
            throw new IllegalArgumentException("API group not found: " + groupName);
        }

        ProducerTemplate template = camelContext.createProducerTemplate();

        List<CompletableFuture<ApiCallResult>> futures =
                group.getSources().entrySet().stream()
                        .map(entry ->
                                CompletableFuture.supplyAsync(
                                        () -> callSingleApi(
                                                entry.getKey(),
                                                entry.getValue(),
                                                pathVariables,
                                                template
                                        ),
                                        apiExecutor
                                )
                        )
                        .toList();

        List<ApiCallResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        List<ApiCallResult> failedApis = results.stream()
                .filter(r -> !r.isSuccess())
                .toList();

        if (!failedApis.isEmpty()) {
            throw new ApiAggregationException(failedApis);
        }

        Map<String, Object> aggregated = new LinkedHashMap<>();
        results.forEach(r -> aggregated.put(r.getKey(), r.getValue()));

        return aggregated;
    }

    private ApiCallResult callSingleApi(
            String key,
            ApiConfigRegistry.Source cfg,
            Map<String, String> pathVariables,
            ProducerTemplate template
    ) {
        String thread = Thread.currentThread().getName();
        log.info("CALL API [" + key + "] on thread: " + thread);

        try {
            if ("db".equalsIgnoreCase(cfg.getType())) {
                return callDb(key, cfg, pathVariables, template, thread);
            } else if ("soap".equalsIgnoreCase(cfg.getType())) {
                return callSoap(key, cfg, pathVariables, template, thread);
            } else {
                return callRest(key, cfg, pathVariables, template, thread);
            }
        } catch (Exception e) {
            return new ApiCallResult(key, cfg.getUrl(), null, e);
        }
    }

    private ApiCallResult callDb(
            String key,
            ApiConfigRegistry.Source cfg,
            Map<String, String> pathVariables,
            ProducerTemplate template,
            String thread
    ) throws Exception {
        DataSource dataSource = dataSourceFactory.getDataSource(cfg.getConfig());

        // Register datasource in Camel registry
        camelContext.getRegistry().bind("dataSource", dataSource);

        // Replace path variables in query if any
        String query = cfg.getQuery();
        for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
            query = query.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        String finalQuery = query;
        String dbUrl = cfg.getConfig().getUrl();

        Exchange response = template.request("direct:callDb", ex -> {
            ex.getIn().setHeader("query", finalQuery);
        });

        String body = response.getMessage().getBody(String.class);

        log.info("DONE DB QUERY [" + key + "] on thread: " + thread);

        if (body == null || body.isBlank()) {
            throw new RuntimeException(
                    "Empty response from database: " + dbUrl
            );
        }

        Object root = JsonPath.read(body, cfg.getPath());
        Object value;

        if (cfg.getFields() == null || cfg.getFields().isEmpty()) {
            value = root;
        } else {
            Map<String, Object> extracted = new LinkedHashMap<>();
            cfg.getFields().forEach((field, path) ->
                    extracted.put(field, JsonPath.read(root, path))
            );
            value = extracted;
        }

        return new ApiCallResult(key, dbUrl, value, null);
    }

    private ApiCallResult callRest(
            String key,
            ApiConfigRegistry.Source cfg,
            Map<String, String> pathVariables,
            ProducerTemplate template,
            String thread
    ) throws Exception {
        String url = cfg.getUrl();
        for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
            url = url.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        String finalUrl = url;
        Exchange response = template.request("direct:callApi", ex -> {
            ex.getIn().setHeader("url", finalUrl);
        });

        Integer status = response.getMessage()
                .getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);

        String body = response.getMessage().getBody(String.class);

        log.info("DONE API [" + key + "] on thread: " + thread);

        if (status == null || status >= 400) {
            throw new RuntimeException(
                    "HTTP " + status + " when calling " + finalUrl
            );
        }

        if (body == null || body.isBlank()) {
            throw new RuntimeException(
                    "Empty response body from " + finalUrl
            );
        }

        Object root = JsonPath.read(body, cfg.getPath());
        Object value;

        if (cfg.getFields() == null || cfg.getFields().isEmpty()) {
            value = root;
        } else {
            Map<String, Object> extracted = new LinkedHashMap<>();
            cfg.getFields().forEach((field, path) ->
                    extracted.put(field, JsonPath.read(root, path))
            );
            value = extracted;
        }

        return new ApiCallResult(key, finalUrl, value, null);
    }

    private ApiCallResult callSoap(
            String key,
            ApiConfigRegistry.Source cfg,
            Map<String, String> pathVariables,
            ProducerTemplate template,
            String thread
    ) throws Exception {
        String url = cfg.getUrl();
        for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
            url = url.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        String finalUrl = url;
        Exchange response = template.request("direct:callApiSoap", ex -> {
            ex.getIn().setHeader("url", finalUrl);
            ex.getIn().setBody(SoapRequestBodyXml.map.get(cfg.getSoapAction()));
        });

        Integer status = response.getMessage()
                .getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);

        String body = response.getMessage().getBody(String.class);

        log.info("DONE API [" + key + "] on thread: " + thread);

        if (status == null || status >= 400) {
            throw new RuntimeException(
                    "HTTP " + status + " when calling " + finalUrl
            );
        }

        if (body == null || body.isBlank()) {
            throw new RuntimeException(
                    "Empty response body from " + finalUrl
            );
        }

        Object root = JsonPath.read(body, cfg.getPath());
        Object value;

        if (cfg.getFields() == null || cfg.getFields().isEmpty()) {
            value = root;
        } else {
            Map<String, Object> extracted = new LinkedHashMap<>();
            cfg.getFields().forEach((field, path) ->
                    extracted.put(field, JsonPath.read(root, path))
            );
            value = extracted;
        }

        return new ApiCallResult(key, finalUrl, value, null);
    }
}
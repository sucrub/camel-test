package com.example.demo.route;

import com.example.demo.exception.ApiAggregationException;
import com.example.demo.service.ApiAggregationService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.apache.camel.http.base.HttpOperationFailedException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AggregateRoute extends RouteBuilder {

    @Autowired
    private ApiAggregationService apiAggregationService;

    @Override
    public void configure() {
        // Báo lỗi cụ thể
        onException(ApiAggregationException.class)
                .handled(true)
                .process(exchange -> {
                    ApiAggregationException ex =
                            exchange.getProperty(Exchange.EXCEPTION_CAUGHT, ApiAggregationException.class);

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("message", "API aggregation failed");

                    List<Map<String, Object>> failedApis = new ArrayList<>();

                    ex.getFailedApis().forEach(r -> {
                        Map<String, Object> api = new LinkedHashMap<>();
                        api.put("key", r.getKey());
                        api.put("url", r.getUrl());
                        api.put("reason", r.getError().getMessage());
                        failedApis.add(api);
                    });

                    body.put("failedApis", failedApis);

                    exchange.getMessage().setBody(body);
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 502);
                })
                .marshal().json();

        restConfiguration()
                .component("servlet")
                .contextPath("/")
                .bindingMode(RestBindingMode.off);

//        // API Call đến địa chỉ /test để direct vào "direct:aggregate-services"
//        rest("/test")
//                .get()
//                .to("direct:aggregate-services");
//
//        // Thực hiện process trong apiAggregationService rồi trả ra kết quả, định dạng về json()
//        from("direct:aggregate-services")
//                .routeId("aggregate-services-route")
//                .process(exchange -> {
//                    Map<String, Object> result = apiAggregationService.aggregateApis("test");
//                    exchange.getMessage().setBody(result);
//                })
//                .marshal().json();
//
//        rest("/test")
//                .get("/{id}")
//                .to("direct:aggregate-services-with-id");
//
//        from("direct:aggregate-services-with-id")
//                .routeId("aggregate-services-with-id-route")
//                .process(exchange -> {
//                    // Ví dụ với truyền id
//                    String id = exchange.getIn().getHeader("id", String.class);
//                    Map<String, String> pathVariables = Map.of("id", id);
//                    Map<String, Object> result = apiAggregationService.aggregateApis("test2", pathVariables);
//                    exchange.getMessage().setBody(result);
//                })
//                .marshal().json();

        rest("/test")
                .get()
                .to("direct:test");

        from("direct:test")
                .routeId("aggregate-services-demo-with-id-route-test")
                .process(exchange -> {
                    Map<String, Object> result = apiAggregationService.aggregateApis("test");
                    exchange.getMessage().setBody(result);
                })
                .marshal().json();

        rest("/demo")
                .get("/{id}")
                .to("direct:demo");

        from("direct:demo")
                .routeId("aggregate-services-demo-with-id-route")
                .process(exchange -> {
                    // Ví dụ với truyền id
                    String id = exchange.getIn().getHeader("id", String.class);
                    Map<String, String> pathVariables = Map.of("id", id);
                    Map<String, Object> result = apiAggregationService.aggregateApis("demo", pathVariables);
                    exchange.getMessage().setBody(result);
                })
                .marshal().json();

        from("direct:callApi")
                .routeId("call-api-route")
                .onException(HttpOperationFailedException.class)
                    .maximumRedeliveries(3) // retry 3 lần
                    .redeliveryDelay(5000) // delay 5s
                    .useExponentialBackOff()
                    .retryAttemptedLogLevel(LoggingLevel.WARN)
                    .logRetryAttempted(true)
                    .handled(false)
                .end()
                .setHeader("Accept-Encoding", constant("identity"))
                .toD("${header.url}?throwExceptionOnFailure=true")
                .convertBodyTo(String.class);

        from("direct:callApiSoap")
                .routeId("call-api-soap-route")
                .onException(HttpOperationFailedException.class)
                    .maximumRedeliveries(3) // retry 3 lần
                    .redeliveryDelay(5000) // delay 5s
                    .useExponentialBackOff()
                    .retryAttemptedLogLevel(LoggingLevel.WARN)
                    .logRetryAttempted(true)
                    .handled(false)
                .end()
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml; charset=utf-8"))
                .setHeader("Accept-Encoding", constant("identity"))
//                .setHeader("SOAPAction", header("soapAction"))
                .toD("${header.url}?throwExceptionOnFailure=true")

                // XML → JSON
                .convertBodyTo(String.class)
                .unmarshal().jacksonXml()
                .marshal().json();

        from("direct:callDb")
                .routeId("call-db-route")
                .onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(5000)
                .useExponentialBackOff()
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .logRetryAttempted(true)
                .handled(false)
                .end()
                .toD("sql:${header.query}?dataSource=#dataSource&outputType=SelectList")
                .marshal().json()
                .convertBodyTo(String.class);
    }
}
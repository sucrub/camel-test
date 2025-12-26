package com.example.demo.exception;

import com.example.demo.dto.ApiCallResult;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class ApiAggregationException extends RuntimeException {
    private final List<ApiCallResult> failedApis;

    public ApiAggregationException(List<ApiCallResult> failedApis) {
        super("One or more APIs failed");
        this.failedApis = failedApis;
    }
}

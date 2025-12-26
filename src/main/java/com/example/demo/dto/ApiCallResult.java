package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiCallResult {

    private final String key;
    private final String url;
    private final Object value;
    private final Throwable error;

    public ApiCallResult(String key, String url, Object value, Throwable error) {
        this.key = key;
        this.url = url;
        this.value = value;
        this.error = error;
    }

    public boolean isSuccess() {
        return error == null;
    }
}

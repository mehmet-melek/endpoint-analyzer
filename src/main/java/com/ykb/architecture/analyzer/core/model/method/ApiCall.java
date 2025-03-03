package com.ykb.architecture.analyzer.core.model.method;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@Data
@Builder
public class ApiCall {
    private String httpMethod;
    @JsonProperty("path")
    private String fullPath;
    private Map<String, String> pathVariables;
    private Map<String, ?> queryParameters;
    private Object requestBody;
    private Object responseBody;
} 
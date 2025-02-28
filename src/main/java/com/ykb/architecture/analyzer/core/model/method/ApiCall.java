package com.ykb.architecture.analyzer.core.model.method;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ApiCall {
    private String httpMethod;
    private String fullPath;
    private Map<String, Object> pathVariables;
    private Map<String, Object> queryParameters;
    private Map<String, Object> requestBody;
    private Map<String, Object> responseBody;
} 
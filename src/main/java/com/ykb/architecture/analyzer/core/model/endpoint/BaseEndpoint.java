package com.ykb.architecture.analyzer.core.model.endpoint;

import com.ykb.architecture.analyzer.core.model.method.EndpointMethod;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
public abstract class BaseEndpoint {
    private String className;
    private String basePath;
    private List<EndpointMethod> methods;
} 
package com.ykb.architecture.analyzer.core.model.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ykb.architecture.analyzer.core.model.endpoint.ConsumedEndpoint;
import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ServiceReport {
    @JsonProperty("environment")
    private String environment;
    
    @JsonProperty("organizationName")
    private String organizationName;
    
    @JsonProperty("productName")
    private String productName;
    
    @JsonProperty("applicationName")
    private String applicationName;
    
    private List<ApiCall> providedEndpoints;
    private List<ConsumedEndpoint> consumedEndpoints;
} 
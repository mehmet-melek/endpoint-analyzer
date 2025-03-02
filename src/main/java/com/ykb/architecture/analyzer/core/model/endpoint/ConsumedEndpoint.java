package com.ykb.architecture.analyzer.core.model.endpoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ConsumedEndpoint {
    @JsonProperty("clientOrganizationName")
    private String clientOrganizationName;
    
    @JsonProperty("clientProductName")
    private String clientProductName;
    
    @JsonProperty("clientApplicationName")
    private String clientApplicationName;
    
    private List<ApiCall> apiCalls;
} 
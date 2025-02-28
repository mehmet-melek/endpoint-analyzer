package com.ykb.architecture.analyzer.core.model.endpoint;

import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ProvidedEndpoint {
    private String applicationName;  // renamed from className
    private List<ApiCall> apiCalls; // renamed from methods
} 
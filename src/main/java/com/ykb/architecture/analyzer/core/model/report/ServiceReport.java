package com.ykb.architecture.analyzer.core.model.report;

import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import com.ykb.architecture.analyzer.core.model.endpoint.ConsumedEndpoint;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ServiceReport {
    private String applicationName;
    private List<ApiCall> providedEndpoints;
    private List<ConsumedEndpoint> consumedEndpoints;
} 
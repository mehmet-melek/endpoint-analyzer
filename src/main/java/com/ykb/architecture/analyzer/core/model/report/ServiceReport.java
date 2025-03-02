package com.ykb.architecture.analyzer.core.model.report;

import com.ykb.architecture.analyzer.core.model.endpoint.ConsumedEndpoint;
import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ServiceReport {
    private List<ApiCall> providedEndpoints;
    private List<ConsumedEndpoint> consumedEndpoints;
} 
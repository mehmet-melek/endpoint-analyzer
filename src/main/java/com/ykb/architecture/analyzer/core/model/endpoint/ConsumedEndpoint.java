package com.ykb.architecture.analyzer.core.model.endpoint;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class ConsumedEndpoint extends BaseEndpoint {
    private String clientName;
} 
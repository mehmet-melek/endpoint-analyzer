package com.ykb.architecture.testservices.microservice_insight_engine.model.graph;

import lombok.Data;
import java.util.List;

@Data
public class ApplicationEdge {
    private String source;
    private String target;
    private List<EdgeDetail> details;

    public ApplicationEdge() {
    }

    public ApplicationEdge(String source, String target, List<EdgeDetail> details) {
        this.source = source;
        this.target = target;
        this.details = details;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public List<EdgeDetail> getDetails() {
        return details;
    }

    public void setDetails(List<EdgeDetail> details) {
        this.details = details;
    }

    // Getters and setters...
} 
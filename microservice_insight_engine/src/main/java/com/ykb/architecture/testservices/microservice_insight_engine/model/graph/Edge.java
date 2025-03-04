package com.ykb.architecture.testservices.microservice_insight_engine.model.graph;

import lombok.Data;
import java.util.Set;

@Data
public class Edge {
    private String source;
    private String target;
    private Set<String> applications;  // Set kullanarak mükerrer application'ları önlüyoruz

    public Edge() {
    }

    public Edge(String source, String target, Set<String> applications) {
        this.source = source;
        this.target = target;
        this.applications = applications;
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

    public Set<String> getApplications() {
        return applications;
    }

    public void setApplications(Set<String> applications) {
        this.applications = applications;
    }
}
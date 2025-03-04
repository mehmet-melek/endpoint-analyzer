package com.ykb.architecture.testservices.microservice_insight_engine.model.graph;

import lombok.Data;

@Data
public class EdgeDetail {
    private String method;
    private String path;

    public EdgeDetail() {
    }

    public EdgeDetail(String method, String path) {
        this.method = method;
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
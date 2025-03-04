package com.ykb.architecture.testservices.microservice_insight_engine.model.graph;

import lombok.Data;
import java.util.List;

@Data
public class GraphData<T> {
    private List<Node> nodes;
    private List<T> edges;

    public GraphData() {
    }

    public GraphData(List<Node> nodes, List<T> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<T> getEdges() {
        return edges;
    }

    public void setEdges(List<T> edges) {
        this.edges = edges;
    }
}
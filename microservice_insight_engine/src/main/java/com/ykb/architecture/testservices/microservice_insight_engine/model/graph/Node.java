package com.ykb.architecture.testservices.microservice_insight_engine.model.graph;

import lombok.Data;

@Data
public class Node {
    private String name;
    private String label;
    private String type;


    public Node() {
    }

    public Node(String name, String label, String type) {
        this.name = name;
        this.label = label;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
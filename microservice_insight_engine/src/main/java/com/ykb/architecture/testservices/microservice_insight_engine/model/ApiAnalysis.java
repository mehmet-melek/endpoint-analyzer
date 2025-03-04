package com.ykb.architecture.testservices.microservice_insight_engine.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "api_analysis")
public class ApiAnalysis {
    @Id
    private String id;
    
    private String organizationName;
    private String productName;
    private String applicationName;

    // JSON string olarak endpoint verileri
    private String currentConsumedEndpoints;
    private String currentProvidedEndpoints;
    private String previousConsumedEndpoints;
    private String previousProvidedEndpoints;

    private LocalDateTime updatedAt;

    public ApiAnalysis() {
    }

    public ApiAnalysis(String id, String organizationName, String productName, String applicationName, String currentConsumedEndpoints, String currentProvidedEndpoints, String previousConsumedEndpoints, String previousProvidedEndpoints, LocalDateTime updatedAt) {
        this.id = id;
        this.organizationName = organizationName;
        this.productName = productName;
        this.applicationName = applicationName;
        this.currentConsumedEndpoints = currentConsumedEndpoints;
        this.currentProvidedEndpoints = currentProvidedEndpoints;
        this.previousConsumedEndpoints = previousConsumedEndpoints;
        this.previousProvidedEndpoints = previousProvidedEndpoints;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getCurrentConsumedEndpoints() {
        return currentConsumedEndpoints;
    }

    public void setCurrentConsumedEndpoints(String currentConsumedEndpoints) {
        this.currentConsumedEndpoints = currentConsumedEndpoints;
    }

    public String getCurrentProvidedEndpoints() {
        return currentProvidedEndpoints;
    }

    public void setCurrentProvidedEndpoints(String currentProvidedEndpoints) {
        this.currentProvidedEndpoints = currentProvidedEndpoints;
    }

    public String getPreviousConsumedEndpoints() {
        return previousConsumedEndpoints;
    }

    public void setPreviousConsumedEndpoints(String previousConsumedEndpoints) {
        this.previousConsumedEndpoints = previousConsumedEndpoints;
    }

    public String getPreviousProvidedEndpoints() {
        return previousProvidedEndpoints;
    }

    public void setPreviousProvidedEndpoints(String previousProvidedEndpoints) {
        this.previousProvidedEndpoints = previousProvidedEndpoints;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
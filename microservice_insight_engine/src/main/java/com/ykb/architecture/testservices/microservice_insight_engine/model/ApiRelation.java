package com.ykb.architecture.testservices.microservice_insight_engine.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "api_relations")
public class ApiRelation {

    @Id
    private String id;

    private String consumerOrganization;
    private String consumerProduct;
    private String consumerApplication;

    private String providerOrganization;
    private String providerProduct;
    private String providerApplication;

    private String method; // Örneğin, GET, POST vb.
    private String path;   // Örneğin, /customers/{customerId}

    // Getters, Setters, Constructors

    public ApiRelation() {
    }

    public ApiRelation(String id, String consumerOrganization, String consumerProduct, String consumerApplication, String providerOrganization, String providerProduct, String providerApplication, String method, String path) {
        this.id = id;
        this.consumerOrganization = consumerOrganization;
        this.consumerProduct = consumerProduct;
        this.consumerApplication = consumerApplication;
        this.providerOrganization = providerOrganization;
        this.providerProduct = providerProduct;
        this.providerApplication = providerApplication;
        this.method = method;
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConsumerOrganization() {
        return consumerOrganization;
    }

    public void setConsumerOrganization(String consumerOrganization) {
        this.consumerOrganization = consumerOrganization;
    }

    public String getConsumerProduct() {
        return consumerProduct;
    }

    public void setConsumerProduct(String consumerProduct) {
        this.consumerProduct = consumerProduct;
    }

    public String getConsumerApplication() {
        return consumerApplication;
    }

    public void setConsumerApplication(String consumerApplication) {
        this.consumerApplication = consumerApplication;
    }

    public String getProviderOrganization() {
        return providerOrganization;
    }

    public void setProviderOrganization(String providerOrganization) {
        this.providerOrganization = providerOrganization;
    }

    public String getProviderProduct() {
        return providerProduct;
    }

    public void setProviderProduct(String providerProduct) {
        this.providerProduct = providerProduct;
    }

    public String getProviderApplication() {
        return providerApplication;
    }

    public void setProviderApplication(String providerApplication) {
        this.providerApplication = providerApplication;
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
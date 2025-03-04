package com.ykb.architecture.testservices.microservice_insight_engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykb.architecture.testservices.microservice_insight_engine.model.ApiRelation;
import com.ykb.architecture.testservices.microservice_insight_engine.repository.ApiRelationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApiRelationService {

    @Autowired
    private ApiRelationRepository apiRelationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public List<ApiRelation> createApiRelations(String organizationName, String productName, String applicationName, JsonNode analysisJson) {
        // Önce bu uygulamaya (source) ait tüm ilişkileri sil
        apiRelationRepository.deleteByConsumerApplication(applicationName);
        
        List<ApiRelation> relations = new ArrayList<>();
        
        // Consumed endpoints'lerden yeni ilişkileri oluştur
        JsonNode consumedEndpoints = analysisJson.get("consumedEndpoints");
        if (consumedEndpoints != null && consumedEndpoints.isArray()) {
            for (JsonNode consumed : consumedEndpoints) {
                String clientOrg = consumed.get("clientOrganizationName").asText();
                String clientProduct = consumed.get("clientProductName").asText();
                String clientApp = consumed.get("clientApplicationName").asText();

                JsonNode apiCalls = consumed.get("apiCalls");
                if (apiCalls != null && apiCalls.isArray()) {
                    for (JsonNode call : apiCalls) {
                        ApiRelation relation = new ApiRelation();
                        
                        // Consumer bilgileri (API'yi çağıran)
                        relation.setConsumerOrganization(organizationName);
                        relation.setConsumerProduct(productName);
                        relation.setConsumerApplication(applicationName);
                        
                        // Provider bilgileri (API'yi sunan)
                        relation.setProviderOrganization(clientOrg);
                        relation.setProviderProduct(clientProduct);
                        relation.setProviderApplication(clientApp);
                        
                        // API detayları
                        relation.setMethod(call.get("httpMethod").asText());
                        relation.setPath(call.get("path").asText());
                        
                        relations.add(relation);
                    }
                }
            }
        }
        
        return apiRelationRepository.saveAll(relations);
    }

    public List<ApiRelation> getAllRelations() {
        return apiRelationRepository.findAll();
    }

    public List<ApiRelation> getRelationsByConsumer(String organization, String product, String application) {
        return apiRelationRepository.findByConsumerOrganizationAndConsumerProductAndConsumerApplication(
                organization, product, application);
    }

    public List<ApiRelation> getRelationsByProvider(String organization, String product, String application) {
        return apiRelationRepository.findByProviderOrganizationAndProviderProductAndProviderApplication(
                organization, product, application);
    }
}

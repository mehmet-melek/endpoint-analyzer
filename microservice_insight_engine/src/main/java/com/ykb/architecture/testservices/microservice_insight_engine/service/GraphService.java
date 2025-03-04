package com.ykb.architecture.testservices.microservice_insight_engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykb.architecture.testservices.microservice_insight_engine.model.ApiAnalysis;
import com.ykb.architecture.testservices.microservice_insight_engine.model.ApiRelation;
import com.ykb.architecture.testservices.microservice_insight_engine.model.graph.*;
import com.ykb.architecture.testservices.microservice_insight_engine.repository.ApiAnalysisRepository;
import com.ykb.architecture.testservices.microservice_insight_engine.repository.ApiRelationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraphService {

    @Autowired
    private ApiRelationRepository apiRelationRepository;

    @Autowired
    private ApiAnalysisRepository apiAnalysisRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public GraphData<ApplicationEdge> generateApplicationGraph() {
        List<ApiRelation> relations = apiRelationRepository.findAll();
        
        // Unique application isimleri için Set oluştur
        Set<String> applicationNames = new HashSet<>();
        relations.forEach(relation -> {
            applicationNames.add(relation.getConsumerApplication());
            applicationNames.add(relation.getProviderApplication());
        });

        // Node'ları oluştur
        List<Node> nodes = applicationNames.stream()
                .map(appName -> {
                    Node node = new Node();
                    node.setName(appName);
                    node.setLabel(appName.substring(appName.lastIndexOf('.') + 1));
                    node.setType("application");
                    return node;
                })
                .collect(Collectors.toList());

        // Edge'leri oluştur
        Map<String, ApplicationEdge> edgeMap = new HashMap<>();
        
        relations.forEach(relation -> {
            String edgeKey = relation.getConsumerApplication() + "->" + relation.getProviderApplication();
            
            ApplicationEdge edge = edgeMap.computeIfAbsent(edgeKey, k -> {
                ApplicationEdge e = new ApplicationEdge();
                e.setSource(relation.getConsumerApplication());
                e.setTarget(relation.getProviderApplication());
                e.setDetails(new ArrayList<>());
                return e;
            });
            
            // HTTP detaylarını ekle
            EdgeDetail detail = new EdgeDetail();
            detail.setMethod(relation.getMethod());
            detail.setPath(relation.getPath());
            
            // Mükerrer kayıtları önle
            if (!containsSameDetail(edge.getDetails(), detail)) {
                edge.getDetails().add(detail);
            }
        });

        GraphData<ApplicationEdge> graphData = new GraphData<>();
        graphData.setNodes(nodes);
        graphData.setEdges(new ArrayList<>(edgeMap.values()));
        
        return graphData;
    }

    private boolean containsSameDetail(List<EdgeDetail> details, EdgeDetail newDetail) {
        return details.stream().anyMatch(detail ->
                detail.getMethod().equals(newDetail.getMethod()) &&
                detail.getPath().equals(newDetail.getPath())
        );
    }

    public GraphData<Edge> generateProductGraph() {
        List<ApiRelation> relations = apiRelationRepository.findAll();
        Set<String> productNames = new HashSet<>();
        Map<String, Edge> edgeMap = new HashMap<>();

        relations.forEach(relation -> {
            String sourceProduct = relation.getConsumerProduct();
            String targetProduct = relation.getProviderProduct();
            
            if (isValidProduct(sourceProduct) && isValidProduct(targetProduct)) {
                productNames.add(sourceProduct);
                productNames.add(targetProduct);

                String edgeKey = sourceProduct + "->" + targetProduct;
                Edge edge = edgeMap.computeIfAbsent(edgeKey, k -> {
                    Edge e = new Edge();
                    e.setSource(sourceProduct);
                    e.setTarget(targetProduct);
                    e.setApplications(new HashSet<>());
                    return e;
                });

                // Application'ı edge'e ekle
                String applicationLabel = relation.getConsumerApplication()
                    .substring(relation.getConsumerApplication().lastIndexOf('.') + 1);
                edge.getApplications().add(applicationLabel);
            }
        });

        // Node'ları oluştur
        List<Node> nodes = productNames.stream()
                .map(productName -> {
                    Node node = new Node();
                    node.setName(productName);
                    node.setLabel(productName.substring(productName.lastIndexOf('.') + 1));
                    node.setType("product");
                    return node;
                })
                .collect(Collectors.toList());

        GraphData<Edge> graphData = new GraphData<>();
        graphData.setNodes(nodes);
        graphData.setEdges(new ArrayList<>(edgeMap.values()));

        return graphData;
    }

    private boolean isValidProduct(String productName) {
        return productName != null && !productName.isEmpty() && 
               !productName.equalsIgnoreCase("unknown") && 
               !productName.equalsIgnoreCase("null");
    }
} 
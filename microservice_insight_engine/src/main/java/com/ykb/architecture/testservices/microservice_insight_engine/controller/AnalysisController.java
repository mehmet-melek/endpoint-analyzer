package com.ykb.architecture.testservices.microservice_insight_engine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykb.architecture.testservices.microservice_insight_engine.model.ApiAnalysis;
import com.ykb.architecture.testservices.microservice_insight_engine.model.ApiRelation;
import com.ykb.architecture.testservices.microservice_insight_engine.service.AnalysisService;
import com.ykb.architecture.testservices.microservice_insight_engine.service.ApiRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiRelationService apiRelationService;

    // POST /analysis: Yeni analiz verisini kaydet veya güncelle
    @PostMapping
    public ResponseEntity<ApiAnalysis> saveOrUpdateAnalysis(
            @RequestParam (required = true) String organizationName,
            @RequestParam (required = true) String productName,
            @RequestParam (required = true) String applicationName,
            @RequestBody (required = true) JsonNode analysisJson) {

        ApiAnalysis analysis = new ApiAnalysis();
        analysis.setOrganizationName(organizationName);
        analysis.setProductName(productName);
        analysis.setApplicationName(applicationName);
        // JSON verilerini string olarak sakla
        analysis.setCurrentProvidedEndpoints(analysisJson.get("providedEndpoints").toString());
        analysis.setCurrentConsumedEndpoints(analysisJson.get("consumedEndpoints").toString());
        
        // API ilişkilerini oluştur ve kaydet
        apiRelationService.createApiRelations(organizationName, productName, applicationName, analysisJson);
        
        ApiAnalysis savedAnalysis = analysisService.saveOrUpdateAnalysis(analysis);
        return new ResponseEntity<>(savedAnalysis, HttpStatus.CREATED);
    }

    // GET /analysis: Belirtilen meta bilgilere göre analiz verisini getir
    @GetMapping
    public ResponseEntity<ApiAnalysis> getAnalysis(
            @RequestParam String organizationName,
            @RequestParam String productName,
            @RequestParam String applicationName) {

        return analysisService.findAnalysis(organizationName, productName, applicationName)
                .map(analysis -> new ResponseEntity<>(analysis, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/relations")
    public ResponseEntity<List<ApiRelation>> getRelations(
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String type) {
        
        List<ApiRelation> relations;
        if (organization != null && product != null && application != null) {
            if ("consumer".equals(type)) {
                relations = apiRelationService.getRelationsByConsumer(organization, product, application);
            } else if ("provider".equals(type)) {
                relations = apiRelationService.getRelationsByProvider(organization, product, application);
            } else {
                relations = apiRelationService.getAllRelations();
            }
        } else {
            relations = apiRelationService.getAllRelations();
        }
        
        return new ResponseEntity<>(relations, HttpStatus.OK);
    }

    // Diff hesaplama, bağımlılık ve graph endpoint'leri eklenebilir
}
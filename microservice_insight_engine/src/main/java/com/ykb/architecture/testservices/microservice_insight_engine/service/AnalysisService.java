package com.ykb.architecture.testservices.microservice_insight_engine.service;

import com.ykb.architecture.testservices.microservice_insight_engine.model.ApiAnalysis;
import com.ykb.architecture.testservices.microservice_insight_engine.repository.ApiAnalysisRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AnalysisService {

    @Autowired
    private ApiAnalysisRepository analysisRepository;

    public ApiAnalysis saveOrUpdateAnalysis(ApiAnalysis newAnalysis) {
        Optional<ApiAnalysis> existingOpt = analysisRepository.findByOrganizationNameAndProductNameAndApplicationName(
                newAnalysis.getOrganizationName(),
                newAnalysis.getProductName(),
                newAnalysis.getApplicationName());

        if(existingOpt.isPresent()){
            ApiAnalysis existing = existingOpt.get();
            // Mevcut current değerlerini previous'e taşıyın
            existing.setPreviousConsumedEndpoints(existing.getCurrentConsumedEndpoints());
            existing.setPreviousProvidedEndpoints(existing.getCurrentProvidedEndpoints());

            // Yeni gelen verileri current alanlarına atayın
            existing.setCurrentConsumedEndpoints(newAnalysis.getCurrentConsumedEndpoints());
            existing.setCurrentProvidedEndpoints(newAnalysis.getCurrentProvidedEndpoints());
            existing.setUpdatedAt(LocalDateTime.now());

            return analysisRepository.save(existing);
        } else {
            newAnalysis.setUpdatedAt(LocalDateTime.now());
            return analysisRepository.save(newAnalysis);
        }
    }

    public Optional<ApiAnalysis> findAnalysis(String organizationName, String productName, String applicationName) {
        return analysisRepository.findByOrganizationNameAndProductNameAndApplicationName(
                organizationName, productName, applicationName);
    }

    // Diff hesaplama ve diğer iş mantığı metotlarını ekleyin.
}
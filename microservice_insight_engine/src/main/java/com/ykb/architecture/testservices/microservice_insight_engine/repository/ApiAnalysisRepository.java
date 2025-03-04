package com.ykb.architecture.testservices.microservice_insight_engine.repository;

import com.ykb.architecture.testservices.microservice_insight_engine.model.ApiAnalysis;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ApiAnalysisRepository extends MongoRepository<ApiAnalysis, String> {
    Optional<ApiAnalysis> findByOrganizationNameAndProductNameAndApplicationName(
            String organizationName, String productName, String applicationName);
}
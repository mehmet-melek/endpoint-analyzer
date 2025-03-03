package com.ykb.architecture.analyzer.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.ykb.architecture.analyzer.core.model.endpoint.ConsumedEndpoint;
import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import com.ykb.architecture.analyzer.core.model.report.ServiceReport;
import com.ykb.architecture.analyzer.parser.consumer.FeignClientParser;
import com.ykb.architecture.analyzer.parser.provider.RestControllerParser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
public class AnalyzerService {
    private final String sourceRoot;
    private final RestControllerParser restControllerParser;
    private final FeignClientParser feignClientParser;

    public AnalyzerService(String sourceRoot, String configPath) {
        this.sourceRoot = sourceRoot;
        this.restControllerParser = new RestControllerParser(sourceRoot);
        this.feignClientParser = new FeignClientParser(sourceRoot, configPath);
    }

    public ServiceReport analyze() {
        log.info("Starting analysis");
        List<ApiCall> providedEndpoints = new ArrayList<>();
        List<ConsumedEndpoint> consumedEndpoints = new ArrayList<>();

        try {
            try (Stream<Path> paths = Files.walk(Path.of(sourceRoot))) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> path.toString().endsWith(".java"))
                     .forEach(path -> processJavaFile(path, providedEndpoints, consumedEndpoints));
            }
        } catch (IOException e) {
            log.error("Failed to scan source directory: {}, error: {}", sourceRoot, e.getMessage());
        }

        ServiceReport report = buildServiceReport(providedEndpoints, consumedEndpoints);
        log.info("Analysis completed. Found {} provided endpoints and {} consumed clients", 
            report.getProvidedEndpoints().size(), report.getConsumedEndpoints().size());
        return report;
    }

    private void processJavaFile(Path path, List<ApiCall> providedEndpoints, List<ConsumedEndpoint> consumedEndpoints) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new File(path.toString()));

            // Parse REST controllers
            List<ApiCall> newProvidedEndpoints = restControllerParser.parse(cu);
            if (!newProvidedEndpoints.isEmpty()) {
                log.info("Found REST controller in {} with {} endpoints", getSimpleFileName(path), newProvidedEndpoints.size());
                providedEndpoints.addAll(newProvidedEndpoints);
            }

            // Parse Feign clients
            List<ConsumedEndpoint> newConsumedEndpoints = feignClientParser.parse(cu);
            if (!newConsumedEndpoints.isEmpty()) {
                newConsumedEndpoints.forEach(endpoint -> 
                    log.info("Found Feign client '{}' in {} with {} endpoints", 
                        endpoint.getClientApplicationName(), getSimpleFileName(path), endpoint.getApiCalls().size()));
                consumedEndpoints.addAll(newConsumedEndpoints);
            }

        } catch (Exception e) {
            log.error("Failed to parse file: {}, error: {}", path, e.getMessage());
        }
    }

    private ServiceReport buildServiceReport(List<ApiCall> providedEndpoints, List<ConsumedEndpoint> consumedEndpoints) {
        // Group consumed endpoints by client name and merge their API calls
        Map<String, ConsumedEndpoint> mergedEndpoints = new HashMap<>();
        
        for (ConsumedEndpoint endpoint : consumedEndpoints) {
            String clientKey = endpoint.getClientApplicationName();
            
            if (mergedEndpoints.containsKey(clientKey)) {
                // Add API calls to existing endpoint
                ConsumedEndpoint existing = mergedEndpoints.get(clientKey);
                List<ApiCall> mergedCalls = new ArrayList<>(existing.getApiCalls());
                mergedCalls.addAll(endpoint.getApiCalls());
                existing.setApiCalls(mergedCalls);
            } else {
                // Create new endpoint
                mergedEndpoints.put(clientKey, endpoint);
            }
        }

        return ServiceReport.builder()
                .providedEndpoints(providedEndpoints)
                .consumedEndpoints(new ArrayList<>(mergedEndpoints.values()))
                .build();
    }

    private String getSimpleFileName(Path path) {
        return path.getFileName().toString();
    }
} 
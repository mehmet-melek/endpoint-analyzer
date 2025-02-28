package com.ykb.architecture.analyzer.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.ykb.architecture.analyzer.core.model.endpoint.ConsumedEndpoint;
import com.ykb.architecture.analyzer.core.model.endpoint.ProvidedEndpoint;
import com.ykb.architecture.analyzer.core.model.report.ServiceReport;
import com.ykb.architecture.analyzer.parser.consumer.FeignClientParser;
import com.ykb.architecture.analyzer.parser.provider.RestControllerParser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class AnalyzerService {
    private final String sourceRoot;
    private final String serviceName;
    private final RestControllerParser restControllerParser;
    private final FeignClientParser feignClientParser;

    public AnalyzerService(String sourceRoot, String serviceName) {
        this.sourceRoot = sourceRoot;
        this.serviceName = serviceName;
        this.restControllerParser = new RestControllerParser(sourceRoot);
        this.feignClientParser = new FeignClientParser(sourceRoot);
    }

    public ServiceReport analyze() {
        List<ProvidedEndpoint> providedEndpoints = new ArrayList<>();
        List<ConsumedEndpoint> consumedEndpoints = new ArrayList<>();

        try {
            // Find all Java files recursively
            try (Stream<Path> paths = Files.walk(Path.of(sourceRoot))) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> path.toString().endsWith(".java"))
                     .forEach(path -> processJavaFile(path, providedEndpoints, consumedEndpoints));
            }
        } catch (IOException e) {
            log.error("Error walking through source directory: {}", sourceRoot, e);
        }

        return ServiceReport.builder()
                .serviceName(serviceName)
                .providedEndpoints(providedEndpoints)
                .consumedEndpoints(consumedEndpoints)
                .build();
    }

    private void processJavaFile(Path path, List<ProvidedEndpoint> providedEndpoints, List<ConsumedEndpoint> consumedEndpoints) {
        try {
            log.debug("Processing file: {}", path);
            CompilationUnit cu = StaticJavaParser.parse(new File(path.toString()));

            // Parse REST controllers
            providedEndpoints.addAll(restControllerParser.parse(cu));

            // Parse Feign clients
            consumedEndpoints.addAll(feignClientParser.parse(cu));

        } catch (Exception e) {
            log.error("Error processing file: {}", path, e);
        }
    }
} 
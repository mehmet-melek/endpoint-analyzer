package com.ykb.architecture.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ykb.architecture.analyzer.core.model.report.ServiceReport;
import com.ykb.architecture.analyzer.service.AnalyzerService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class Application {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar endpoint-analyzer.jar <source-root> <service-name> [output-file]");
            System.exit(1);
        }

        try {
            String sourceRoot = args[0];
            String serviceName = args[1];
            String outputFile = args.length > 2 ? args[2] : "endpoint-analysis.json";

            log.info("Analyzing service {} from source root: {}", serviceName, sourceRoot);
            
            AnalyzerService analyzerService = new AnalyzerService(sourceRoot, serviceName);
            ServiceReport report = analyzerService.analyze();

            // Write report to file
            Path outputPath = Paths.get(outputFile);
            objectMapper.writeValue(outputPath.toFile(), report);
            
            log.info("Analysis complete. Report written to: {}", outputPath.toAbsolutePath());

        } catch (Exception e) {
            log.error("Error during analysis", e);
            System.exit(1);
        }
    }
} 
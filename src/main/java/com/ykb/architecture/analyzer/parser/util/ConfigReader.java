package com.ykb.architecture.analyzer.parser.util;

import org.yaml.snakeyaml.Yaml;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ConfigReader {
    private final Map<String, Object> config;

    public ConfigReader(String configPath) {
        Map<String, Object> loadedConfig;
        Yaml yaml = new Yaml();
        
        try (InputStream in = new FileInputStream(configPath)) {
            loadedConfig = yaml.load(in);
        } catch (Exception e) {
            log.error("Failed to load config file: {}", configPath, e);
            loadedConfig = Map.of();
        }
        
        this.config = loadedConfig;
    }

    public Optional<String> resolveParametricValue(String value) {
        if (!isParametric(value)) {
            return Optional.empty();
        }

        // Extract parameter name from ${...}
        String paramName = value.substring(2, value.length() - 1); // Remove ${ and }
        String[] parts = paramName.split("\\.");

        // Navigate through nested maps
        Map<String, Object> current = config;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i].toLowerCase());
            if (!(next instanceof Map)) {
                return Optional.empty();
            }
            current = (Map<String, Object>) next;
        }

        // Get final value
        Object result = current.get(parts[parts.length - 1].toLowerCase());
        return result != null ? Optional.of(result.toString()) : Optional.empty();
    }

    private boolean isParametric(String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }
} 
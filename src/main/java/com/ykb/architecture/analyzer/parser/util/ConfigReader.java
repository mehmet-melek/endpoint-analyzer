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

    public Optional<String> resolveParametricValue(String parameterValue) {
        if (parameterValue == null || !parameterValue.startsWith("${") || !parameterValue.endsWith("}")) {
            return Optional.empty();
        }

        // Extract parameter name without ${...}
        String paramName = parameterValue.substring(2, parameterValue.length() - 1);
        
        // Split the parameter path (e.g., "endpoint.postOffice" -> ["endpoint", "postOffice"])
        String[] pathParts = paramName.split("\\.");
        
        Map<String, Object> currentMap = config;
        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = pathParts[i];
            // Case-insensitive key search in the current map level
            String matchingKey = findKeyIgnoreCase(currentMap, part);
            if (matchingKey == null) {
                log.warn("Could not find configuration for path part: {}", part);
                return Optional.empty();
            }
            
            Object value = currentMap.get(matchingKey);
            if (!(value instanceof Map)) {
                log.warn("Configuration path part is not a map: {}", part);
                return Optional.empty();
            }
            currentMap = (Map<String, Object>) value;
        }

        // Case-insensitive search for the final key
        String finalKey = findKeyIgnoreCase(currentMap, pathParts[pathParts.length - 1]);
        if (finalKey == null) {
            log.warn("Could not find configuration for key: {}", pathParts[pathParts.length - 1]);
            return Optional.empty();
        }

        Object value = currentMap.get(finalKey);
        return Optional.ofNullable(value).map(Object::toString);
    }

    /**
     * Finds a key in the map ignoring case
     * @param map The map to search in
     * @param searchKey The key to search for
     * @return The actual key from the map, or null if not found
     */
    private String findKeyIgnoreCase(Map<String, Object> map, String searchKey) {
        return map.keySet().stream()
                .filter(key -> key.equalsIgnoreCase(searchKey))
                .findFirst()
                .orElse(null);
    }
} 
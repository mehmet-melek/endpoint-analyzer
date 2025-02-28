package com.ykb.architecture.analyzer.parser.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PathResolver {

    public String combinePaths(String basePath, String methodPath) {
        String normalizedBasePath = normalizePath(basePath);
        String normalizedMethodPath = normalizePath(methodPath);

        if (normalizedBasePath.isEmpty()) {
            return normalizedMethodPath;
        }
        if (normalizedMethodPath.isEmpty()) {
            return normalizedBasePath;
        }

        return normalizedBasePath + (normalizedMethodPath.startsWith("/") ? "" : "/") + normalizedMethodPath;
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }

        String normalized = path.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
} 
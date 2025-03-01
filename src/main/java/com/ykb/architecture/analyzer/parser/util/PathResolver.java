package com.ykb.architecture.analyzer.parser.util;

import lombok.experimental.UtilityClass;

/**
 * Utility class for handling URL and path manipulations.
 * Ensures proper path combinations and formatting.
 */
@UtilityClass
public class PathResolver {

    /**
     * Combines two paths ensuring proper slash handling.
     * Removes duplicate slashes and normalizes the result.
     */
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
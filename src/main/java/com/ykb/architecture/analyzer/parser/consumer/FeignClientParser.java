package com.ykb.architecture.analyzer.parser.consumer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import com.ykb.architecture.analyzer.core.model.endpoint.ConsumedEndpoint;
import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import com.ykb.architecture.analyzer.parser.base.AbstractEndpointParser;
import com.ykb.architecture.analyzer.parser.util.AnnotationParser;
import com.ykb.architecture.analyzer.parser.util.ConfigReader;
import com.ykb.architecture.analyzer.parser.util.PathResolver;
import com.ykb.architecture.analyzer.parser.util.TypeResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Parses Feign clients and their endpoint declarations.
 * Handles @FeignClient annotation and its various attributes.
 */
@Slf4j
public class FeignClientParser extends AbstractEndpointParser<ConsumedEndpoint> {

    private static final String FEIGN_CLIENT = "FeignClient";
    private static final String REQUEST_MAPPING = "RequestMapping";
    private static final String GET_MAPPING = "GetMapping";
    private static final String POST_MAPPING = "PostMapping";
    private static final String PUT_MAPPING = "PutMapping";
    private static final String DELETE_MAPPING = "DeleteMapping";
    private static final String PATCH_MAPPING = "PatchMapping";

    private final TypeResolver typeResolver;
    private final ConfigReader configReader;

    public FeignClientParser(String sourceRoot, String configPath) {
        this.typeResolver = new TypeResolver(sourceRoot);
        this.configReader = new ConfigReader(configPath);
    }

    @Override
    protected boolean shouldParse(ClassOrInterfaceDeclaration classDeclaration) {
        return classDeclaration.isInterface() && 
               AnnotationParser.hasAnnotation(classDeclaration, FEIGN_CLIENT);
    }

    @Override
    protected ConsumedEndpoint parseClass(ClassOrInterfaceDeclaration classDeclaration) {
        String clientName = getClientName(classDeclaration);
        String basePath = getBasePath(classDeclaration);
        List<ApiCall> apiCalls = parseApiCalls(classDeclaration, basePath);

        return ConsumedEndpoint.builder()
                .clientApplicationName(extractApplicationName(clientName))
                .clientOrganizationName(extractOrganizationName(clientName))
                .clientProductName(extractProductName(clientName))
                .apiCalls(apiCalls)
                .build();
    }

    /**
     * Extracts client name from @FeignClient annotation.
     * Follows priority: name -> value -> url -> class name -> unknown
     */
    private String getClientName(ClassOrInterfaceDeclaration classDeclaration) {
        // First try to get 'name' attribute
        Optional<String> nameAttr = AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "name");
        if (nameAttr.isPresent()) {
            String name = nameAttr.get();
            Optional<String> resolvedName = configReader.resolveParametricValue(name);
            if (resolvedName.isPresent()) {
                return resolvedName.get();
            }
        }

        // Then try 'value' attribute
        Optional<String> valueAttr = AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "value");
        if (valueAttr.isPresent()) {
            String value = valueAttr.get();
            Optional<String> resolvedValue = configReader.resolveParametricValue(value);
            if (resolvedValue.isPresent()) {
                return resolvedValue.get();
            }
        }

        // If no name/value with parameters, try to get 'url' attribute
        Optional<String> url = AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "url");
        if (url.isPresent()) {
            return url.get();
        }

        // If no url, try to get single value
        Optional<String> singleValue = AnnotationParser.getAnnotationSingleValue(classDeclaration, FEIGN_CLIENT);
        if (singleValue.isPresent()) {
            String value = singleValue.get();
            Optional<String> resolvedValue = configReader.resolveParametricValue(value);
            if (resolvedValue.isPresent()) {
                return resolvedValue.get();
            }
            return value;
        }

        log.warn("No name, value, url or direct value found for FeignClient: {}", classDeclaration.getNameAsString());
        return "unknown-application";
    }

    /**
     * Combines base path from @FeignClient and @RequestMapping.
     * Ensures proper path concatenation with leading/trailing slashes.
     */
    private String getBasePath(ClassOrInterfaceDeclaration classDeclaration) {
        // First try path from @FeignClient
        String feignPath = AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "path")
                .orElse("");

        // Then try @RequestMapping
        String requestPath = AnnotationParser.getAnnotationValue(classDeclaration, REQUEST_MAPPING, "value")
                .or(() -> AnnotationParser.getAnnotationValue(classDeclaration, REQUEST_MAPPING, "path"))
                .or(() -> AnnotationParser.getAnnotationSingleValue(classDeclaration, REQUEST_MAPPING))
                .orElse("");

        // Combine both paths
        return PathResolver.combinePaths(feignPath, requestPath);
    }

    private List<ApiCall> parseApiCalls(ClassOrInterfaceDeclaration classDeclaration, String basePath) {
        List<ApiCall> apiCalls = new ArrayList<>();
        
        for (MethodDeclaration method : classDeclaration.getMethods()) {
            if (isEndpointMethod(method)) {
                try {
                    ApiCall apiCall = parseApiCall(method, basePath);
                    apiCalls.add(apiCall);
                } catch (Exception e) {
                    log.error("Error parsing method in {}", classDeclaration.getNameAsString(), e);
                }
            }
        }
        
        return apiCalls;
    }

    private ApiCall parseApiCall(MethodDeclaration method, String basePath) {
        String methodPath = getMethodPath(method);
        String fullPath = PathResolver.combinePaths(basePath, methodPath);

        return ApiCall.builder()
                .httpMethod(determineHttpMethod(method))
                .fullPath(fullPath)
                .pathVariables(parsePathVariables(method))
                .queryParameters(parseQueryParameters(method))
                .requestBody(parseRequestBody(method))
                .responseBody(parseResponseBody(method))
                .build();
    }

    private boolean isEndpointMethod(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(a -> {
                    String name = a.getNameAsString();
                    return name.equals(REQUEST_MAPPING) ||
                           name.equals(GET_MAPPING) ||
                           name.equals(POST_MAPPING) ||
                           name.equals(PUT_MAPPING) ||
                           name.equals(DELETE_MAPPING) ||
                           name.equals(PATCH_MAPPING);
                });
    }

    private String getMethodPath(MethodDeclaration method) {
        for (String annotation : List.of(REQUEST_MAPPING, GET_MAPPING, POST_MAPPING, PUT_MAPPING, DELETE_MAPPING, PATCH_MAPPING)) {
            Optional<String> path = AnnotationParser.getAnnotationValue(method, annotation, "value")
                    .or(() -> AnnotationParser.getAnnotationSingleValue(method, annotation));
            if (path.isPresent()) {
                return path.get();
            }
        }
        return "";
    }

    private String determineHttpMethod(MethodDeclaration method) {
        if (method.getAnnotationByName(GET_MAPPING).isPresent()) return "GET";
        if (method.getAnnotationByName(POST_MAPPING).isPresent()) return "POST";
        if (method.getAnnotationByName(PUT_MAPPING).isPresent()) return "PUT";
        if (method.getAnnotationByName(DELETE_MAPPING).isPresent()) return "DELETE";
        if (method.getAnnotationByName(PATCH_MAPPING).isPresent()) return "PATCH";
        
        Optional<String> requestMethod = AnnotationParser.getAnnotationValue(method, REQUEST_MAPPING, "method");
        return requestMethod.map(m -> m.replace("RequestMethod.", "")).orElse("GET");
    }

    private Map<String, String> parsePathVariables(MethodDeclaration method) {
        Map<String, String> pathVariables = new HashMap<>();
        method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "PathVariable"))
                .forEach(p -> {
                    String name = AnnotationParser.getAnnotationValue(p, "PathVariable", "value")
                            .orElse(p.getNameAsString());
                    pathVariables.put(name, p.getTypeAsString());
                });
        return pathVariables.isEmpty() ? null : pathVariables;
    }

    private Map<String, String> parseQueryParameters(MethodDeclaration method) {
        Map<String, String> queryParams = new HashMap<>();
        method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "RequestParam"))
                .forEach(p -> {
                    // First try 'value' attribute, then 'name' attribute for parameter name
                    String name = AnnotationParser.getAnnotationValue(p, "RequestParam", "value")
                            .or(() -> AnnotationParser.getAnnotationValue(p, "RequestParam", "name"))
                            .orElse(p.getNameAsString());
                    
                    // For Feign clients, just add parameter name and type
                    queryParams.put(name, p.getTypeAsString());
                });
        return queryParams.isEmpty() ? null : queryParams;
    }

    private Object parseRequestBody(MethodDeclaration method) {
        Optional<Parameter> requestBodyParam = findRequestBodyParameter(method);
        if (requestBodyParam.isEmpty()) {
            return null;
        }

        // Check if parameter has @Valid or @Validated annotation
        boolean isValidated = requestBodyParam.get().getAnnotations().stream()
                .anyMatch(a -> {
                    String name = a.getNameAsString();
                    return name.equals("Valid") || 
                           name.equals("Validated") ||
                           name.equals("javax.validation.Valid") ||
                           name.equals("org.springframework.validation.annotation.Validated");
                });

        Type paramType = requestBodyParam.get().getType();
        return typeResolver.resolveRequestBody(paramType, isValidated);
    }

    private Object parseResponseBody(MethodDeclaration method) {
        // If method returns void, there's no response body
        if (method.getType().isVoidType()) {
            return null;
        }

        Type returnType = method.getType();
        
        // Special handling for ResponseEntity
        if (returnType.asString().startsWith("ResponseEntity")) {
            return typeResolver.resolveResponseEntityType(returnType);
        }

        // For all other types
        return typeResolver.resolveResponseBody(returnType);
    }

    private Optional<Parameter> findRequestBodyParameter(MethodDeclaration method) {
        return method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "RequestBody"))
                .findFirst();
    }

    private String extractOrganizationName(String clientName) {
        if (clientName.startsWith("http") || clientName.startsWith("${") || !clientName.contains(".")) {
            return null;
        }
        
        String[] parts = clientName.split("\\.");
        if (parts.length >= 3) {
            return parts[0];  // Return just the organization name
        }
        
        return null;
    }

    private String extractProductName(String clientName) {
        if (clientName.startsWith("http") || clientName.startsWith("${") || !clientName.contains(".")) {
            return null;
        }
        
        String[] parts = clientName.split("\\.");
        if (parts.length >= 3) {
            return parts[1];  // Return just the product name
        }
        
        return null;
    }

    // Yeni bir metod ekleyelim - clientApplicationName için
    private String extractApplicationName(String clientName) {
        if (clientName.startsWith("http") || clientName.startsWith("${") || !clientName.contains(".")) {
            return clientName;  // URL veya parametrik değer ise aynen döndür
        }
        
        String[] parts = clientName.split("\\.");
        if (parts.length >= 3) {
            return parts[2];  // Return just the application name
        }
        
        return clientName;  // Eğer format uygun değilse tam ismi döndür
    }

    @Override
    public List<ConsumedEndpoint> parse(CompilationUnit compilationUnit) {
        List<ConsumedEndpoint> endpoints = new ArrayList<>();
        
        try {
            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(this::shouldParse)
                    .map(this::parseClass)
                    .forEach(endpoint -> {
                        log.debug("Found FeignClient: {} with {} API calls", 
                            endpoint.getClientApplicationName(), endpoint.getApiCalls().size());
                        endpoints.add(endpoint);
                    });
        } catch (Exception e) {
            log.error("Error parsing file: {}", 
                compilationUnit.getStorage().map(s -> s.getPath().toString()).orElse("unknown"), e);
        }
        
        return endpoints;
    }
} 
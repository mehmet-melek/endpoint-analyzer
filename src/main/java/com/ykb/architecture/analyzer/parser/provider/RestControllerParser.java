package com.ykb.architecture.analyzer.parser.provider;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import com.ykb.architecture.analyzer.parser.base.AbstractEndpointParser;
import com.ykb.architecture.analyzer.parser.util.AnnotationParser;
import com.ykb.architecture.analyzer.parser.util.PathResolver;
import com.ykb.architecture.analyzer.parser.util.TypeResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Parses Spring REST controllers and their endpoints.
 * Handles @RestController and @Controller annotations with their mappings.
 */
@Slf4j
public class RestControllerParser extends AbstractEndpointParser<ApiCall> {

    private static final String REST_CONTROLLER = "RestController";
    private static final String CONTROLLER = "Controller";
    private static final String REQUEST_MAPPING = "RequestMapping";
    private static final String GET_MAPPING = "GetMapping";
    private static final String POST_MAPPING = "PostMapping";
    private static final String PUT_MAPPING = "PutMapping";
    private static final String DELETE_MAPPING = "DeleteMapping";
    private static final String PATCH_MAPPING = "PatchMapping";

    private final TypeResolver typeResolver;

    public RestControllerParser(String sourceRoot) {
        this.typeResolver = new TypeResolver(sourceRoot);
    }

    @Override
    public List<ApiCall> parse(CompilationUnit compilationUnit) {
        List<ApiCall> endpoints = new ArrayList<>();
        
        try {
            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(this::shouldParse)
                    .forEach(classDeclaration -> {
                        String basePath = getBasePath(classDeclaration);
                        List<ApiCall> apiCalls = parseApiCalls(classDeclaration, basePath);
                        log.debug("Found {} endpoints in controller {}", 
                            apiCalls.size(), classDeclaration.getNameAsString());
                        endpoints.addAll(apiCalls);
                    });
        } catch (Exception e) {
            log.error("Error parsing file: {}", 
                compilationUnit.getStorage().map(s -> s.getPath().toString()).orElse("unknown"), e);
        }
        
        return endpoints;
    }

    /**
     * Determines if a class should be parsed as a REST controller.
     * Checks for @RestController or @Controller + @ResponseBody combination.
     */
    @Override
    protected boolean shouldParse(ClassOrInterfaceDeclaration classDeclaration) {
        return AnnotationParser.hasAnnotation(classDeclaration, REST_CONTROLLER) ||
               (AnnotationParser.hasAnnotation(classDeclaration, CONTROLLER) && 
                classDeclaration.getMethods().stream().anyMatch(m -> 
                    AnnotationParser.hasAnnotation(m, "ResponseBody")));
    }

    private String getBasePath(ClassOrInterfaceDeclaration classDeclaration) {
        return AnnotationParser.getAnnotationValue(classDeclaration, REQUEST_MAPPING, "value")
                .or(() -> AnnotationParser.getAnnotationValue(classDeclaration, REQUEST_MAPPING, "path"))
                .or(() -> AnnotationParser.getAnnotationSingleValue(classDeclaration, REQUEST_MAPPING))
                .orElse("");
    }

    private List<ApiCall> parseApiCalls(ClassOrInterfaceDeclaration classDeclaration, String basePath) {
        List<ApiCall> apiCalls = new ArrayList<>();
        
        for (MethodDeclaration method : classDeclaration.getMethods()) {
            if (isEndpointMethod(method)) {
                try {
                    ApiCall apiCall = parseApiCallFromMethod(method, basePath);
                    apiCalls.add(apiCall);
                } catch (Exception e) {
                    log.error("Error parsing method in {}", classDeclaration.getNameAsString(), e);
                }
            }
        }
        
        return apiCalls;
    }

    /**
     * Parses an endpoint method into an ApiCall object.
     * Extracts HTTP method, path, parameters, and request/response bodies.
     */
    private ApiCall parseApiCallFromMethod(MethodDeclaration method, String basePath) {
        String path = determinePath(method, basePath);
        
        return ApiCall.builder()
                .httpMethod(determineHttpMethod(method))
                .fullPath(path)
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
        
        // For @RequestMapping, check method attribute
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
                    String name = AnnotationParser.getAnnotationValue(p, "RequestParam", "value")
                            .orElse(p.getNameAsString());
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

        return typeResolver.resolveResponseBody(method.getType());
    }

    private Optional<Parameter> findRequestBodyParameter(MethodDeclaration method) {
        return method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "RequestBody"))
                .findFirst();
    }

    @Override
    protected ApiCall parseClass(ClassOrInterfaceDeclaration classDeclaration) {
        String basePath = getBasePath(classDeclaration);
        List<ApiCall> apiCalls = parseApiCalls(classDeclaration, basePath);
        return apiCalls.isEmpty() ? null : apiCalls.get(0);
    }

    /**
     * Combines controller base path with method path.
     * Ensures proper path concatenation with leading/trailing slashes.
     */
    private String determinePath(MethodDeclaration method, String basePath) {
        String methodPath = getMethodPath(method);
        return PathResolver.combinePaths(basePath, methodPath);
    }
} 
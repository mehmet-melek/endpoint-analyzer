package com.ykb.architecture.analyzer.parser.provider;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import com.ykb.architecture.analyzer.core.model.method.ApiCall;
import com.ykb.architecture.analyzer.parser.base.AbstractEndpointParser;
import com.ykb.architecture.analyzer.parser.util.AnnotationParser;
import com.ykb.architecture.analyzer.parser.util.PathResolver;
import com.ykb.architecture.analyzer.parser.util.TypeResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

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
        
        // For @RequestMapping, check method attribute
        Optional<String> requestMethod = AnnotationParser.getAnnotationValue(method, REQUEST_MAPPING, "method");
        return requestMethod.map(m -> m.replace("RequestMethod.", "")).orElse("GET");
    }

    private Map<String, Object> parsePathVariables(MethodDeclaration method) {
        Map<String, Object> pathVariables = new HashMap<>();
        method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "PathVariable"))
                .forEach(p -> {
                    String name = AnnotationParser.getAnnotationValue(p, "PathVariable", "value")
                            .orElse(p.getNameAsString());
                    pathVariables.put(name, p.getTypeAsString());
                });
        return pathVariables;
    }

    private Map<String, Object> parseQueryParameters(MethodDeclaration method) {
        Map<String, Object> queryParams = new HashMap<>();
        method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "RequestParam"))
                .forEach(p -> {
                    String name = AnnotationParser.getAnnotationValue(p, "RequestParam", "value")
                            .orElse(p.getNameAsString());
                    queryParams.put(name, p.getTypeAsString());
                });
        return queryParams;
    }

    private Map<String, Object> parseRequestBody(MethodDeclaration method) {
        return method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "RequestBody"))
                .findFirst()
                .map(p -> typeResolver.resolveFields(p.getType()))
                .orElse(null);
    }

    private Map<String, Object> parseResponseBody(MethodDeclaration method) {
        Type returnType = method.getType();
        if (returnType.isVoidType()) {
            return null;
        }

        // Handle ResponseEntity
        if (returnType.asString().startsWith("ResponseEntity")) {
            try {
                Type genericType = returnType.asClassOrInterfaceType()
                        .getTypeArguments()
                        .orElse(new NodeList<>())
                        .stream()
                        .findFirst()
                        .orElse(returnType);
                return typeResolver.resolveFields(genericType);
            } catch (Exception e) {
                log.warn("Could not parse ResponseEntity generic type: {}", e.getMessage());
            }
        }

        // Handle collection types directly
        if (returnType.isClassOrInterfaceType()) {
            return typeResolver.resolveFields(returnType);
        }

        return Map.of("type", returnType.asString());
    }

    @Override
    protected ApiCall parseClass(ClassOrInterfaceDeclaration classDeclaration) {
        String basePath = getBasePath(classDeclaration);
        List<ApiCall> apiCalls = parseApiCalls(classDeclaration, basePath);
        return apiCalls.isEmpty() ? null : apiCalls.get(0);
    }
} 
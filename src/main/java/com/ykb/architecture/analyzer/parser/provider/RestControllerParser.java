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
                    ApiCall apiCall = parseApiCall(method, basePath);
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
    private ApiCall parseApiCall(MethodDeclaration method, String basePath) {
        String methodPath = getMethodPath(method);
        String path = PathResolver.combinePaths(basePath, methodPath);

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

    private Map<String, Object> parsePathVariables(MethodDeclaration method) {
        Map<String, Object> pathVariables = new HashMap<>();
        method.getParameters().stream()
                .filter(p -> AnnotationParser.hasAnnotation(p, "PathVariable"))
                .forEach(p -> {
                    String name = AnnotationParser.getAnnotationValue(p, "PathVariable", "value")
                            .orElse(p.getNameAsString());
                    pathVariables.put(name, p.getTypeAsString());
                });
        return pathVariables.isEmpty() ? null : pathVariables;
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
        return queryParams.isEmpty() ? null : queryParams;
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
        
        // Handle void return type
        if (returnType.isVoidType()) {
            return null;
        }

        // Handle ResponseEntity
        if (returnType.asString().startsWith("ResponseEntity")) {
            try {
                // Check if it's ResponseEntity<Void>
                if (returnType.asString().contains("ResponseEntity<Void>") || 
                    returnType.asString().contains("ResponseEntity<java.lang.Void>")) {
                    return null;
                }

                Type genericType = returnType.asClassOrInterfaceType()
                        .getTypeArguments()
                        .orElse(new NodeList<>())
                        .stream()
                        .findFirst()
                        .orElse(returnType);

                // If generic type is Void or void, return null
                if (genericType.isVoidType() || 
                    genericType.asString().equals("Void") || 
                    genericType.asString().equals("java.lang.Void")) {
                    return null;
                }

                Map<String, Object> fields = typeResolver.resolveFields(genericType);
                return fields == null || fields.isEmpty() ? null : fields;
            } catch (Exception e) {
                log.warn("Could not parse ResponseEntity generic type: {}", e.getMessage());
                return null;
            }
        }

        // Handle collection types directly
        if (returnType.isClassOrInterfaceType()) {
            Map<String, Object> fields = typeResolver.resolveFields(returnType);
            return fields == null || fields.isEmpty() ? null : fields;
        }

        // For primitive types
        String typeName = returnType.asString();
        if (typeName.equals("void") || typeName.equals("java.lang.Void")) {
            return null;
        }

        return Map.of("type", typeName);
    }

    @Override
    protected ApiCall parseClass(ClassOrInterfaceDeclaration classDeclaration) {
        String basePath = getBasePath(classDeclaration);
        List<ApiCall> apiCalls = parseApiCalls(classDeclaration, basePath);
        return apiCalls.isEmpty() ? null : apiCalls.get(0);
    }
} 
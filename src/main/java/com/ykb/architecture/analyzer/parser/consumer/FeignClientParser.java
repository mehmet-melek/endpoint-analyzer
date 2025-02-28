package com.ykb.architecture.analyzer.parser.consumer;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import com.ykb.architecture.analyzer.core.model.endpoint.ConsumedEndpoint;
import com.ykb.architecture.analyzer.core.model.method.EndpointMethod;
import com.ykb.architecture.analyzer.parser.base.AbstractEndpointParser;
import com.ykb.architecture.analyzer.parser.util.AnnotationParser;
import com.ykb.architecture.analyzer.parser.util.PathResolver;
import com.ykb.architecture.analyzer.parser.util.TypeResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

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

    public FeignClientParser(String sourceRoot) {
        this.typeResolver = new TypeResolver(sourceRoot);
    }

    @Override
    protected boolean shouldParse(ClassOrInterfaceDeclaration classDeclaration) {
        return classDeclaration.isInterface() && 
               AnnotationParser.hasAnnotation(classDeclaration, FEIGN_CLIENT);
    }

    @Override
    protected ConsumedEndpoint parseClass(ClassOrInterfaceDeclaration classDeclaration) {
        String className = getClassName(classDeclaration);
        String clientName = getClientName(classDeclaration);
        String basePath = getBasePath(classDeclaration);
        List<EndpointMethod> methods = parseEndpointMethods(classDeclaration, basePath);

        return ConsumedEndpoint.builder()
                .className(className)
                .clientName(clientName)
                .basePath(basePath)
                .methods(methods)
                .build();
    }

    private String getClientName(ClassOrInterfaceDeclaration classDeclaration) {
        // First try to get 'name' or 'value' attribute
        Optional<String> clientName = AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "name")
                .or(() -> AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "value"));
        
        if (clientName.isPresent()) {
            return clientName.get();
        }

        // If no name/value, try to get 'url' attribute
        Optional<String> url = AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "url");
        if (url.isPresent()) {
            return url.get();
        }

        // If no url, try to get single value (direct string value)
        Optional<String> singleValue = AnnotationParser.getAnnotationSingleValue(classDeclaration, FEIGN_CLIENT);
        if (singleValue.isPresent()) {
            return singleValue.get();
        }

        // If nothing found, return unknown client
        log.warn("No name, value, url or direct value found for FeignClient: {}", classDeclaration.getNameAsString());
        return "unknown-client";
    }

    private String getBasePath(ClassOrInterfaceDeclaration classDeclaration) {
        // First try path from @FeignClient
        Optional<String> feignPath = AnnotationParser.getAnnotationValue(classDeclaration, FEIGN_CLIENT, "path");
        if (feignPath.isPresent()) {
            return feignPath.get();
        }

        // Then try @RequestMapping
        return AnnotationParser.getAnnotationValue(classDeclaration, REQUEST_MAPPING, "value")
                .or(() -> AnnotationParser.getAnnotationSingleValue(classDeclaration, REQUEST_MAPPING))
                .orElse("");
    }

    private List<EndpointMethod> parseEndpointMethods(ClassOrInterfaceDeclaration classDeclaration, String basePath) {
        List<EndpointMethod> methods = new ArrayList<>();
        
        for (MethodDeclaration method : classDeclaration.getMethods()) {
            if (isEndpointMethod(method)) {
                try {
                    EndpointMethod endpointMethod = parseMethod(method, basePath);
                    methods.add(endpointMethod);
                } catch (Exception e) {
                    log.error("Error parsing method {}.{}", classDeclaration.getNameAsString(), method.getNameAsString(), e);
                }
            }
        }
        
        return methods;
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

    private EndpointMethod parseMethod(MethodDeclaration method, String basePath) {
        String methodPath = getMethodPath(method);
        String fullPath = PathResolver.combinePaths(basePath, methodPath);
        String httpMethod = determineHttpMethod(method);

        return EndpointMethod.builder()
                .methodName(method.getNameAsString())
                .httpMethod(httpMethod)
                .path(methodPath)
                .fullPath(fullPath)
                .pathVariables(parsePathVariables(method))
                .queryParameters(parseQueryParameters(method))
                .requestBody(parseRequestBody(method))
                .responseBody(parseResponseBody(method))
                .build();
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
} 
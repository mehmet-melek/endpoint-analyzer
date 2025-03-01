package com.ykb.architecture.analyzer.parser.util;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

@Slf4j
public class TypeResolver {
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
        "byte", "short", "int", "long", "float", "double", "boolean", "char", "String"
    );

    private static final Map<String, String> TYPE_MAPPINGS = Map.of(
        "java.util.Date", "Date",
        "java.time.LocalDate", "Date",
        "java.time.LocalDateTime", "DateTime",
        "java.time.ZonedDateTime", "DateTime",
        "java.time.Instant", "DateTime",
        "java.sql.Date", "Date",
        "java.sql.Timestamp", "DateTime"
    );

    private final JavaSymbolSolver symbolSolver;
    private final String sourceRoot;
    private final Set<String> processedTypes = new HashSet<>();

    public TypeResolver(String sourceRoot) {
        this.sourceRoot = sourceRoot;
        
        // Set language level to Java 17 (or your target version)
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        
        // Create type solvers
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        combinedSolver.add(new ReflectionTypeSolver(false));
        
        // Add source root and all its subdirectories
        addSourceDirectories(combinedSolver, new File(sourceRoot));
        
        // Create and configure symbol solver with the new configuration
        this.symbolSolver = new JavaSymbolSolver(combinedSolver);
        StaticJavaParser.setConfiguration(config.setSymbolResolver(symbolSolver));
    }

    private void addSourceDirectories(CombinedTypeSolver solver, File directory) {
        if (!directory.isDirectory()) {
            return;
        }

        try {
            solver.add(new JavaParserTypeSolver(directory));
        } catch (Exception e) {
            log.warn("Could not add directory to solver: {}", directory, e);
        }

        File[] subdirs = directory.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                addSourceDirectories(solver, subdir);
            }
        }
    }

    private String normalizeType(String type) {
        return switch (type) {
            // Primitive types
            case "java.lang.Boolean", "boolean" -> "Boolean";
            case "java.lang.Byte", "byte" -> "Byte";
            case "java.lang.Character", "char" -> "Character";
            case "java.lang.Short", "short" -> "Short";
            case "java.lang.Integer", "int" -> "Integer";
            case "java.lang.Long", "long" -> "Long";
            case "java.lang.Float", "float" -> "Float";
            case "java.lang.Double", "double" -> "Double";
            case "java.lang.String" -> "String";
            case "java.time.LocalDate" -> "LocalDate";
            case "java.time.LocalDateTime" -> "LocalDateTime";
            case "java.time.LocalTime" -> "LocalTime";
            case "java.math.BigDecimal" -> "BigDecimal";
            case "java.math.BigInteger" -> "BigInteger";
            default -> type;
        };
    }

    public Map<String, Object> resolveFields(Type type) {
        if (type == null) {
            return null;
        }

        String typeName = type.asString();

        // Check if it's a mapped type
        if (TYPE_MAPPINGS.containsKey(typeName)) {
            return Map.of("type", TYPE_MAPPINGS.get(typeName));
        }

        // Handle primitive and simple types
        if (PRIMITIVE_TYPES.contains(typeName) || typeName.startsWith("java.lang.")) {
            return Map.of("type", typeName.substring(typeName.lastIndexOf('.') + 1));
        }

        // Handle collection types
        if (typeName.startsWith("java.util.List") || typeName.startsWith("java.util.Set")) {
            Map<String, Object> result = handleCollectionType(type);
            return result == null || result.isEmpty() ? null : result;
        }
        if (typeName.startsWith("java.util.Map")) {
            Map<String, Object> result = handleMapType(type);
            return result == null || result.isEmpty() ? null : result;
        }

        // For custom types, try to resolve their fields
        try {
            Map<String, Object> result = resolveCustomType(type);
            return result == null || result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("Could not resolve custom type: {}", typeName);
            return Map.of("type", typeName);
        }
    }

    private Map<String, Object> handleCollectionType(Type type) {
        try {
            if (!type.isClassOrInterfaceType()) {
                return Map.of("type", "array");
            }

            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if (!classType.getTypeArguments().isPresent()) {
                return Map.of("type", "array");
            }

            Type genericType = classType.getTypeArguments().get().get(0);
            
            // Eğer generic tip primitive veya basit bir tip ise
            if (genericType.isPrimitiveType() || 
                PRIMITIVE_TYPES.contains(genericType.asString()) ||
                genericType.asString().startsWith("java.lang.")) {
                String elementType = normalizeType(genericType.asString());
                return Map.of("type", "array<" + elementType + ">");
            }

            // Diğer tipler için
            Map<String, Object> elementType = resolveFields(genericType);
            if (elementType != null && elementType.size() == 1 && elementType.containsKey("type")) {
                return Map.of("type", "array<" + elementType.get("type") + ">");
            } else {
                return Map.of("type", "array", "items", elementType != null ? elementType : "Object");
            }
        } catch (Exception e) {
            log.debug("Could not resolve collection type {}: {}", type, e.getMessage());
            return Map.of("type", "array");
        }
    }

    private Map<String, Object> handleMapType(Type type) {
        try {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if (classType.getTypeArguments().isPresent()) {
                List<Type> typeArgs = classType.getTypeArguments().get();
                if (typeArgs.size() == 2) {
                    Map<String, Object> keyType = resolveFields(typeArgs.get(0));
                    Map<String, Object> valueType = resolveFields(typeArgs.get(1));
                    String keyTypeName = keyType != null ? keyType.get("type").toString() : "Object";
                    String valueTypeName = valueType != null ? valueType.get("type").toString() : "Object";
                    return Map.of("type", "Map<" + keyTypeName + "," + valueTypeName + ">");
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve map type {}: {}", type, e.getMessage());
        }
        return Map.of("type", "map");
    }

    private Map<String, Object> resolveCustomType(Type type) {
        try {
            String qualifiedName = type.asString();

            // Check if type is an Enum
            try {
                Class<?> clazz = Class.forName(qualifiedName);
                if (clazz.isEnum()) {
                    return resolveEnumType(clazz);
                }
            } catch (ClassNotFoundException ignored) {
                // Continue with normal type resolution
            }

            // Reset processed types for new resolution
            processedTypes.clear();

            // Find and parse the DTO class
            Optional<ClassOrInterfaceDeclaration> dtoClass = findClass(qualifiedName);
            if (dtoClass.isPresent()) {
                return extractFields(dtoClass.get());
            }

        } catch (Exception e) {
            log.warn("Could not resolve type {}: {}", type, e.getMessage());
            return Map.of("type", normalizeType(type.asString()));
        }
        return Map.of();
    }

    private Map<String, Object> resolveEnumType(Class<?> enumClass) {
        try {
            Object[] enumConstants = enumClass.getEnumConstants();
            if (enumConstants == null || enumConstants.length == 0) {
                return Map.of("type", "string", "enum", new ArrayList<>());
            }

            // Try to find the most common return type from enum methods
            String valueType = findEnumValueType(enumClass);
            List<Object> enumValues = new ArrayList<>();
            
            for (Object enumConstant : enumConstants) {
                enumValues.add(enumConstant.toString());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", valueType);
            result.put("enum", enumValues);
            return result;

        } catch (Exception e) {
            log.warn("Could not resolve enum type {}: {}", enumClass.getName(), e.getMessage());
            return Map.of("type", "string");
        }
    }

    private String findEnumValueType(Class<?> enumClass) {
        // Check for common value methods
        try {
            // First check for @JsonValue annotation
            Method jsonValueMethod = findJsonValueMethod(enumClass);
            if (jsonValueMethod != null) {
                return getTypeForClass(jsonValueMethod.getReturnType());
            }

            // Then check for getValue or value method
            Method valueMethod = findValueMethod(enumClass);
            if (valueMethod != null) {
                return getTypeForClass(valueMethod.getReturnType());
            }
        } catch (Exception ignored) {
            // Fall back to string if we can't determine the value type
        }

        return "string";
    }

    private Method findJsonValueMethod(Class<?> enumClass) {
        for (Method method : enumClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(JsonValue.class)) {
                return method;
            }
        }
        return null;
    }

    private Method findValueMethod(Class<?> enumClass) {
        try {
            return enumClass.getMethod("getValue");
        } catch (NoSuchMethodException e1) {
            try {
                return enumClass.getMethod("value");
            } catch (NoSuchMethodException e2) {
                return null;
            }
        }
    }

    private String getTypeForClass(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == Integer.class || clazz == int.class) return "integer";
        if (clazz == Long.class || clazz == long.class) return "long";
        if (clazz == Double.class || clazz == double.class) return "double";
        if (clazz == Boolean.class || clazz == boolean.class) return "boolean";
        return "string";
    }

    private Optional<ClassOrInterfaceDeclaration> findClass(String qualifiedName) {
        try {
            String[] parts = qualifiedName.split("\\.");
            String className = parts[parts.length - 1];
            
            // First try with full qualified name
            File file = new File(sourceRoot + "/" + String.join("/", parts) + ".java");
            if (file.exists()) {
                return parseAndFindClass(file, className);
            }

            // If not found, search recursively in source directory
            return searchClassInDirectory(new File(sourceRoot), className);

        } catch (Exception e) {
            log.warn("Could not find class {}: {}", qualifiedName, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<ClassOrInterfaceDeclaration> searchClassInDirectory(File directory, String className) {
        if (!directory.isDirectory()) {
            return Optional.empty();
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return Optional.empty();
        }

        // First search in current directory
        for (File file : files) {
            if (file.isFile() && file.getName().equals(className + ".java")) {
                return parseAndFindClass(file, className);
            }
        }

        // Then search in subdirectories
        for (File file : files) {
            if (file.isDirectory()) {
                Optional<ClassOrInterfaceDeclaration> result = searchClassInDirectory(file, className);
                if (result.isPresent()) {
                    return result;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<ClassOrInterfaceDeclaration> parseAndFindClass(File file, String className) {
        try {
            log.debug("Trying to parse file: {}", file.getAbsolutePath());
            CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.getClassByName(className);
        } catch (Exception e) {
            log.warn("Could not parse file {}: {}", file.getAbsolutePath(), e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> extractFields(ClassOrInterfaceDeclaration classDeclaration) {
        String className = classDeclaration.getNameAsString();
        if (processedTypes.contains(className)) {
            return Map.of("type", normalizeType(className));
        }
        processedTypes.add(className);

        Map<String, Object> fields = new HashMap<>();
        
        for (FieldDeclaration field : classDeclaration.getFields()) {
            // Skip fields with @JsonIgnore annotation
            boolean isJsonIgnored = field.getAnnotations().stream()
                .anyMatch(a -> {
                    String name = a.getNameAsString();
                    return name.equals("JsonIgnore") || name.equals("com.fasterxml.jackson.annotation.JsonIgnore");
                });
            
            if (isJsonIgnored) {
                continue;  // Skip this field
            }

            // Get field name, checking @JsonProperty first
            String fieldName = field.getAnnotations().stream()
                .filter(a -> {
                    String name = a.getNameAsString();
                    return name.equals("JsonProperty") || name.equals("com.fasterxml.jackson.annotation.JsonProperty");
                })
                .findFirst()
                .map(annotation -> {
                    Optional<String> value = AnnotationParser.getAnnotationValue(annotation, "value");
                    if (value.isPresent()) {
                        return value.get();
                    }
                    return AnnotationParser.getAnnotationSingleValue(annotation)
                            .orElse(field.getVariable(0).getNameAsString());
                })
                .orElse(field.getVariable(0).getNameAsString());

            Type fieldType = field.getVariable(0).getType();
            
            try {
                ResolvedType resolvedType = fieldType.resolve();
                String qualifiedName = resolvedType.describe();

                // Check for JPA relations
                boolean isJpaRelation = field.getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().matches("OneToMany|ManyToOne|OneToOne|ManyToMany"));

                // Check for transient fields
                boolean isTransient = field.getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().equals("Transient") || 
                                 a.getNameAsString().equals("javax.persistence.Transient") ||
                                 a.getNameAsString().equals("jakarta.persistence.Transient"));

                if (isTransient) {
                    continue;  // Skip transient fields
                }

                if (isJpaRelation) {
                    fields.put(fieldName, "relation");
                } else if (isCollectionType(qualifiedName)) {
                    Map<String, Object> collectionType = handleCollectionType(fieldType);
                    fields.put(fieldName, collectionType != null ? collectionType : "array");
                } else if (isJavaType(qualifiedName)) {
                    fields.put(fieldName, normalizeType(resolvedType.describe()));
                } else {
                    Map<String, Object> customType = resolveFields(fieldType);
                    fields.put(fieldName, customType != null ? customType : normalizeType(fieldType.asString()));
                }
            } catch (Exception e) {
                fields.put(fieldName, normalizeType(fieldType.asString()));
            }
        }
        
        return fields;
    }

    private boolean isCollectionType(String qualifiedName) {
        return qualifiedName.startsWith("java.util.List") ||
               qualifiedName.startsWith("java.util.Set") ||
               qualifiedName.startsWith("java.util.Collection");
    }

    private boolean isJavaType(String qualifiedName) {
        return qualifiedName.startsWith("java.") || 
               isPrimitiveType(qualifiedName) ||
               isCommonType(qualifiedName);
    }

    private boolean isPrimitiveType(String type) {
        return type.equals("boolean") || type.equals("byte") || 
               type.equals("char") || type.equals("short") || 
               type.equals("int") || type.equals("long") || 
               type.equals("float") || type.equals("double");
    }

    private boolean isCommonType(String type) {
        return type.equals("String") || type.equals("Integer") || 
               type.equals("Long") || type.equals("Double") || 
               type.equals("Boolean") || type.equals("Float");
    }
} 
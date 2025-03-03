package com.ykb.architecture.analyzer.parser.util;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Resolves Java types into a standardized format for API documentation.
 * Handles primitive types, collections, enums, and custom objects.
 */
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

    /**
     * Resolves fields of a given type into a map representation.
     * @param type The Java type to resolve
     * @return Map containing field information, or null if type cannot be resolved
     */
    public Map<String, Object> resolveFields(Type type) {
        if (type == null) {
            return null;
        }

        try {
            return resolveFields(type.resolve());
        } catch (Exception e) {
            log.warn("Could not resolve type: {}, error: {}", type, e.getMessage());
            return createFieldDefinition(normalizeType(type.asString()), false);
        }
    }

    private Map<String, Object> resolveFields(ResolvedType resolvedType) {
        if (resolvedType == null) {
            return null;
        }

        try {
            String qualifiedName = resolvedType.describe();

            // Handle primitive and known types
            if (isPrimitiveType(qualifiedName) || isCommonType(qualifiedName)) {
                return createFieldDefinition(normalizeType(qualifiedName), false);
            }

            // For class types, get all fields including inherited ones
            if (resolvedType.isReferenceType()) {
                Map<String, Object> fields = new LinkedHashMap<>();
                ResolvedReferenceType referenceType = resolvedType.asReferenceType();
                
                // Get fields from all ancestor classes
                List<ResolvedReferenceType> ancestors = new ArrayList<>();
                ancestors.add(referenceType);
                ancestors.addAll(referenceType.getAllAncestors());

                for (ResolvedReferenceType ancestor : ancestors) {
                    // Skip java.lang.Object
                    if (ancestor.getQualifiedName().equals("java.lang.Object")) {
                        continue;
                    }

                    // Get declared fields for this class/interface
                    for (ResolvedFieldDeclaration field : ancestor.getDeclaredFields()) {
                        String fieldName = field.getName();
                        ResolvedType fieldType = field.getType();

                        // Skip if already processed (child class fields take precedence)
                        if (fields.containsKey(fieldName)) {
                            continue;
                        }

                        // Check for JPA relations and transient fields
                        if (isTransientField(field) || isJpaRelation(field)) {
                            continue;
                        }

                        // Process the field
                        if (isCollectionType(fieldType.describe())) {
                            Map<String, Object> collectionType = handleCollectionType(fieldType);
                            fields.put(fieldName, collectionType);
                        } else if (isJavaType(fieldType.describe())) {
                            fields.put(fieldName, createFieldDefinition(normalizeType(fieldType.describe()), false));
                        } else {
                            Map<String, Object> customType = resolveFields(fieldType);
                            fields.put(fieldName, customType != null ? 
                                    customType : 
                                    createFieldDefinition(normalizeType(fieldType.describe()), false));
                        }
                    }
                }
                
                return fields.isEmpty() ? null : fields;
            }

            return createFieldDefinition(normalizeType(qualifiedName), false);
        } catch (Exception e) {
            log.warn("Could not resolve type: {}, error: {}", resolvedType, e.getMessage());
            return createFieldDefinition(normalizeType(resolvedType.describe()), false);
        }
    }

    private boolean isTransientField(ResolvedFieldDeclaration field) {
        try {
            return field.toAst()
                    .filter(f -> f instanceof FieldDeclaration)
                    .map(f -> (FieldDeclaration) f)
                    .map(f -> f.getAnnotations().stream()
                            .map(AnnotationExpr::getNameAsString)
                            .anyMatch(name -> name.equals("Transient")))
                    .orElse(false);
        } catch (Exception e) {
            log.debug("Could not check transient for field: {}", field.getName());
            return false;
        }
    }

    private boolean isJpaRelation(ResolvedFieldDeclaration field) {
        try {
            return field.toAst()
                    .filter(f -> f instanceof FieldDeclaration)
                    .map(f -> (FieldDeclaration) f)
                    .map(f -> f.getAnnotations().stream()
                            .map(AnnotationExpr::getNameAsString)
                            .anyMatch(name -> name.matches("OneToMany|ManyToOne|OneToOne|ManyToMany")))
                    .orElse(false);
        } catch (Exception e) {
            log.debug("Could not check JPA relations for field: {}", field.getName());
            return false;
        }
    }

    /**
     * Handles collection types (List, Set) and their generic parameters.
     * Returns a simplified array representation.
     */
    private Map<String, Object> handleCollectionType(ResolvedType type) {
        try {
            String typeName = type.describe();
            if (!typeName.contains("<")) {
                return createFieldDefinition("array", false);
            }

            String genericTypeName = typeName.substring(typeName.indexOf('<') + 1, typeName.lastIndexOf('>'));
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "array");
            result.put("required", false);

            // For complex types, try to resolve their fields
            try {
                Optional<ClassOrInterfaceDeclaration> dtoClass = findClass(genericTypeName);
                if (dtoClass.isPresent()) {
                    Map<String, Object> itemFields = extractFields(dtoClass.get());
                    if (itemFields != null && !itemFields.isEmpty()) {
                        // Move ignoreUnknown to collection level if present
                        if (itemFields.containsKey("ignoreUnknown")) {
                            result.put("ignoreUnknown", itemFields.remove("ignoreUnknown"));
                        }
                        result.put("items", itemFields.get("items"));
                        return result;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not resolve collection item type {}: {}", genericTypeName, e.getMessage());
            }

            result.put("items", createFieldDefinition(genericTypeName, false));
            return result;

        } catch (Exception e) {
            log.debug("Could not resolve collection type {}: {}", type, e.getMessage());
            return createFieldDefinition("array", false);
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

    /**
     * Processes enum types, including their values and @JsonValue annotations.
     * @return Map containing enum type and possible values
     */
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
        Map<String, Object> fields = new LinkedHashMap<>();
        
        // Check for @JsonIgnoreProperties
        boolean ignoreUnknown = hasJsonIgnoreProperties(classDeclaration);
        if (ignoreUnknown) {
            fields.put("ignoreUnknown", true);
        }

        // Add items object to hold field definitions
        Map<String, Object> items = new LinkedHashMap<>();
        fields.put("items", items);
        
        for (FieldDeclaration field : classDeclaration.getFields()) {
            // Skip fields with @JsonIgnore
            if (hasAnnotation(field, "JsonIgnore")) {
                continue;
            }

            // Get field name from @JsonProperty if present
            String fieldName = field.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals("JsonProperty"))
                .findFirst()
                .map(this::getJsonPropertyValue)
                .orElse(field.getVariable(0).getNameAsString());

            Type fieldType = field.getVariable(0).getType();
            
            try {
                ResolvedType resolvedType = fieldType.resolve();
                String qualifiedName = resolvedType.describe();
                
                // Check for required annotations
                boolean isRequired = hasRequiredAnnotation(field);

                if (isCollectionType(qualifiedName)) {
                    Map<String, Object> collectionType = handleCollectionType(resolvedType);
                    if (collectionType != null) {
                        collectionType.put("required", isRequired);
                        items.put(fieldName, collectionType);
                    }
                } else if (isJavaType(qualifiedName)) {
                    items.put(fieldName, createFieldDefinition(normalizeType(resolvedType.describe()), isRequired));
                } else {
                    // For custom types (DTOs), check their @JsonIgnoreProperties too
                    Map<String, Object> customType = resolveFields(fieldType);
                    if (customType != null) {
                        customType.put("required", isRequired);
                        items.put(fieldName, customType);
                    } else {
                        items.put(fieldName, createFieldDefinition(normalizeType(fieldType.asString()), isRequired));
                    }
                }
            } catch (Exception e) {
                items.put(fieldName, createFieldDefinition(normalizeType(fieldType.asString()), false));
            }
        }
        
        return fields;
    }

    private boolean hasAnnotation(FieldDeclaration field, String annotationName) {
        return field.getAnnotations().stream()
            .anyMatch(a -> {
                String name = a.getNameAsString();
                return name.equals(annotationName) || 
                       name.equals("com.fasterxml.jackson.annotation." + annotationName) ||
                       name.equals("javax.persistence." + annotationName) ||
                       name.equals("jakarta.persistence." + annotationName);
            });
    }

    private boolean hasRequiredAnnotation(FieldDeclaration field) {
        return field.getAnnotations().stream()
            .anyMatch(a -> {
                String name = a.getNameAsString();
                return name.equals("NotNull") || 
                       name.equals("NotEmpty") || 
                       name.equals("NotBlank") ||
                       name.equals("jakarta.validation.constraints.NotNull") ||
                       name.equals("jakarta.validation.constraints.NotEmpty") ||
                       name.equals("jakarta.validation.constraints.NotBlank") ||
                       name.equals("javax.validation.constraints.NotNull") ||
                       name.equals("javax.validation.constraints.NotEmpty") ||
                       name.equals("javax.validation.constraints.NotBlank") ||
                       name.equals("org.jetbrains.annotations.NotNull") ||
                       name.equals("lombok.NonNull");
            });
    }

    private String getJsonPropertyValue(AnnotationExpr annotation) {
        try {
            // Check if it's a single value annotation: @JsonProperty("value")
            if (!annotation.toString().contains("value=")) {
                return annotation.toString()
                    .replaceAll("@JsonProperty\\(\"(.+)\"\\)", "$1")
                    .replaceAll("@com.fasterxml.jackson.annotation.JsonProperty\\(\"(.+)\"\\)", "$1");
            }
            
            // If it has named parameters: @JsonProperty(value="value")
            return annotation.toString()
                .replaceAll(".*value\\s*=\\s*\"([^\"]+)\".*", "$1");
        } catch (Exception e) {
            log.debug("Could not extract JsonProperty value from: {}", annotation);
            return null;
        }
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

    private boolean hasJpaRelationAnnotation(FieldDeclaration field) {
        return field.getAnnotations().stream()
            .anyMatch(a -> {
                String name = a.getNameAsString();
                return name.equals("OneToMany") || 
                       name.equals("ManyToOne") || 
                       name.equals("OneToOne") || 
                       name.equals("ManyToMany") ||
                       name.equals("javax.persistence.OneToMany") ||
                       name.equals("javax.persistence.ManyToOne") ||
                       name.equals("javax.persistence.OneToOne") ||
                       name.equals("javax.persistence.ManyToMany") ||
                       name.equals("jakarta.persistence.OneToMany") ||
                       name.equals("jakarta.persistence.ManyToOne") ||
                       name.equals("jakarta.persistence.OneToOne") ||
                       name.equals("jakarta.persistence.ManyToMany");
            });
    }

    private Map<String, Object> createFieldDefinition(String type, boolean required) {
        Map<String, Object> fieldDef = new LinkedHashMap<>();  // Use LinkedHashMap to maintain order
        fieldDef.put("type", type);
        fieldDef.put("required", required);
        return fieldDef;
    }

    private boolean hasJsonIgnoreProperties(ClassOrInterfaceDeclaration classDeclaration) {
        // First check direct annotation on the class
        if (classDeclaration.getAnnotations().stream()
                .anyMatch(a -> {
                    String name = a.getNameAsString();
                    if (!name.equals("JsonIgnoreProperties") && 
                        !name.equals("com.fasterxml.jackson.annotation.JsonIgnoreProperties")) {
                        return false;
                    }
                    return a.toString().contains("ignoreUnknown = true") || 
                           a.toString().contains("ignoreUnknown=true");
                })) {
            return true;
        }
        
        // Then check if parent classes have this annotation
        if (classDeclaration.getExtendedTypes().isNonEmpty()) {
            try {
                String parentClassName = classDeclaration.getExtendedTypes().get(0).getNameAsString();
                Optional<ClassOrInterfaceDeclaration> parentClass = findClass(parentClassName);
                if (parentClass.isPresent()) {
                    return hasJsonIgnoreProperties(parentClass.get());
                }
            } catch (Exception e) {
                log.debug("Could not check parent class for JsonIgnoreProperties: {}", e.getMessage());
            }
        }
        
        return false;
    }

    public Map<String, Object> resolveRequestBody(Type type, boolean isValidated) {
        if (type == null) {
            return null;
        }

        try {
            return resolveRequestFields(type.resolve(), isValidated);
        } catch (Exception e) {
            log.warn("Could not resolve request type: {}", type, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> resolveResponseBody(Type type) {
        if (type == null) {
            return null;
        }

        try {
            return resolveResponseFields(type.resolve());
        } catch (Exception e) {
            log.warn("Could not resolve response type: {}", type, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> resolveRequestFields(ResolvedType resolvedType, boolean isValidated) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // Get class declaration
        Optional<ClassOrInterfaceDeclaration> classDecl = findClass(resolvedType.describe());
        if (classDecl.isEmpty()) {
            return null;
        }

        // Check for @JsonIgnoreProperties at class level if validated
        if (isValidated) {
            boolean ignoreUnknown = hasJsonIgnoreProperties(classDecl.get());
            result.put("ignoreUnknown", ignoreUnknown);
        } else {
            // If not validated, always set ignoreUnknown to false
            result.put("ignoreUnknown", false);
        }

        // Add items object
        Map<String, Object> items = new LinkedHashMap<>();
        result.put("items", items);

        // Process fields
        extractRequestFields(classDecl.get(), items, isValidated);

        return result;
    }

    private Map<String, Object> resolveResponseFields(ResolvedType resolvedType) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // Get class declaration
        Optional<ClassOrInterfaceDeclaration> classDecl = findClass(resolvedType.describe());
        if (classDecl.isEmpty()) {
            return null;
        }

        // Add items object
        Map<String, Object> items = new LinkedHashMap<>();
        result.put("items", items);

        // Process fields
        extractResponseFields(classDecl.get(), items);

        return result;
    }

    private void extractRequestFields(ClassOrInterfaceDeclaration classDeclaration, Map<String, Object> fields, boolean isValidated) {
        for (FieldDeclaration field : classDeclaration.getFields()) {
            // Skip fields with @JsonIgnore or JPA relations
            if (hasAnnotation(field, "JsonIgnore") || hasJpaRelationAnnotation(field)) {
                continue;
            }

            // Get field name from @JsonProperty if present
            String fieldName = field.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals("JsonProperty"))
                .findFirst()
                .map(this::getJsonPropertyValue)
                .orElse(field.getVariable(0).getNameAsString());

            Type fieldType = field.getVariable(0).getType();
            
            try {
                ResolvedType resolvedType = fieldType.resolve();
                String qualifiedName = resolvedType.describe();
                
                // Check for required annotations only if validated
                boolean isRequired = isValidated && hasRequiredAnnotation(field);

                if (isCollectionType(qualifiedName)) {
                    Map<String, Object> collectionType = handleRequestCollectionType(resolvedType, isValidated);
                    if (collectionType != null) {
                        collectionType.put("required", isRequired);
                        fields.put(fieldName, collectionType);
                    }
                } else if (isJavaType(qualifiedName)) {
                    fields.put(fieldName, createFieldDefinition(normalizeType(resolvedType.describe()), isRequired));
                } else {
                    Map<String, Object> customType = resolveRequestFields(resolvedType, isValidated);
                    if (customType != null) {
                        Map<String, Object> fieldDef = new LinkedHashMap<>();
                        fieldDef.put("type", normalizeType(resolvedType.describe()));
                        fieldDef.put("required", isRequired);
                        if (customType.containsKey("ignoreUnknown")) {
                            fieldDef.put("ignoreUnknown", customType.get("ignoreUnknown"));
                        }
                        if (customType.containsKey("items")) {
                            fieldDef.put("items", customType.get("items"));
                        }
                        fields.put(fieldName, fieldDef);
                    } else {
                        fields.put(fieldName, createFieldDefinition(normalizeType(fieldType.asString()), isRequired));
                    }
                }
            } catch (Exception e) {
                fields.put(fieldName, createFieldDefinition(normalizeType(fieldType.asString()), false));
            }
        }
    }

    private void extractResponseFields(ClassOrInterfaceDeclaration classDeclaration, Map<String, Object> fields) {
        for (FieldDeclaration field : classDeclaration.getFields()) {
            // Skip fields with @JsonIgnore or JPA relations
            if (hasAnnotation(field, "JsonIgnore") || hasJpaRelationAnnotation(field)) {
                continue;
            }

            // Get field name from @JsonProperty if present
            String fieldName = field.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals("JsonProperty"))
                .findFirst()
                .map(this::getJsonPropertyValue)
                .orElse(field.getVariable(0).getNameAsString());

            Type fieldType = field.getVariable(0).getType();
            
            try {
                ResolvedType resolvedType = fieldType.resolve();
                String qualifiedName = resolvedType.describe();

                if (isCollectionType(qualifiedName)) {
                    Map<String, Object> collectionType = handleResponseCollectionType(resolvedType);
                    fields.put(fieldName, collectionType);
                } else if (isJavaType(qualifiedName)) {
                    fields.put(fieldName, Map.of("type", normalizeType(resolvedType.describe())));
                } else {
                    Map<String, Object> customType = resolveResponseFields(resolvedType);
                    fields.put(fieldName, customType != null ? 
                            customType : 
                            Map.of("type", normalizeType(fieldType.asString())));
                }
            } catch (Exception e) {
                fields.put(fieldName, Map.of("type", normalizeType(fieldType.asString())));
            }
        }
    }

    private Map<String, Object> handleRequestCollectionType(ResolvedType type, boolean isValidated) {
        try {
            String typeName = type.describe();
            if (!typeName.contains("<")) {
                return Map.of("type", "array");
            }

            String genericTypeName = typeName.substring(typeName.indexOf('<') + 1, typeName.lastIndexOf('>'));
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "array");

            // For complex types, try to resolve their fields
            try {
                Optional<ClassOrInterfaceDeclaration> dtoClass = findClass(genericTypeName);
                if (dtoClass.isPresent()) {
                    // Check for @JsonIgnoreProperties at class level
                    if (isValidated) {
                        boolean ignoreUnknown = hasJsonIgnoreProperties(dtoClass.get());
                        result.put("ignoreUnknown", ignoreUnknown);
                    } else {
                        // If not validated, always set ignoreUnknown to false
                        result.put("ignoreUnknown", false);
                    }

                    // Get the fields without wrapping in another "items" object
                    Map<String, Object> itemFields = new LinkedHashMap<>();
                    extractRequestFields(dtoClass.get(), itemFields, isValidated);
                    
                    if (!itemFields.isEmpty()) {
                        result.put("items", itemFields);
                        return result;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not resolve collection item type {}: {}", genericTypeName, e.getMessage());
            }

            result.put("items", Map.of("type", genericTypeName));
            return result;

        } catch (Exception e) {
            log.debug("Could not resolve collection type {}: {}", type, e.getMessage());
            return Map.of("type", "array");
        }
    }

    private Map<String, Object> handleResponseCollectionType(ResolvedType type) {
        try {
            String typeName = type.describe();
            if (!typeName.contains("<")) {
                return Map.of("type", "array");
            }

            String genericTypeName = typeName.substring(typeName.indexOf('<') + 1, typeName.lastIndexOf('>'));
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "array");

            // For complex types, try to resolve their fields
            try {
                Optional<ClassOrInterfaceDeclaration> dtoClass = findClass(genericTypeName);
                if (dtoClass.isPresent()) {
                    Map<String, Object> itemFields = new LinkedHashMap<>();
                    extractResponseFields(dtoClass.get(), itemFields);
                    if (!itemFields.isEmpty()) {
                        result.put("items", itemFields);
                        return result;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not resolve collection item type {}: {}", genericTypeName, e.getMessage());
            }

            result.put("items", Map.of("type", genericTypeName));
            return result;

        } catch (Exception e) {
            log.debug("Could not resolve collection type {}: {}", type, e.getMessage());
            return Map.of("type", "array");
        }
    }
} 
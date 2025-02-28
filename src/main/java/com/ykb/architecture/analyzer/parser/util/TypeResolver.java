package com.ykb.architecture.analyzer.parser.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;

@Slf4j
public class TypeResolver {
    private final JavaSymbolSolver symbolSolver;
    private final String sourceRoot;
    private final Set<String> processedTypes = new HashSet<>();

    public TypeResolver(String sourceRoot) {
        this.sourceRoot = sourceRoot;
        
        // Create type solvers
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        combinedSolver.add(new ReflectionTypeSolver(false));
        
        // Add source root and all its subdirectories
        addSourceDirectories(combinedSolver, new File(sourceRoot));
        
        // Create and configure symbol solver
        this.symbolSolver = new JavaSymbolSolver(combinedSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
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
        Map<String, Object> fields = new HashMap<>();
        try {
            // Handle Map types specially
            if (type.asString().startsWith("Map<")) {
                return resolveMapType(type);
            }

            ResolvedType resolvedType = type.resolve();
            String qualifiedName = resolvedType.describe();

            // Reset processed types for new resolution
            processedTypes.clear();

            // Handle collection types
            if (isCollectionType(qualifiedName)) {
                return resolveCollectionType(type);
            }

            // Handle common Java types directly
            if (isJavaType(qualifiedName)) {
                return Map.of("type", normalizeType(qualifiedName));
            }

            // Find and parse the DTO class
            Optional<ClassOrInterfaceDeclaration> dtoClass = findClass(qualifiedName);
            if (dtoClass.isPresent()) {
                return extractFields(dtoClass.get());
            }

        } catch (Exception e) {
            log.warn("Could not resolve type {}: {}", type, e.getMessage());
            return Map.of("type", normalizeType(type.asString()));
        }
        return fields;
    }

    private Map<String, Object> resolveMapType(Type type) {
        try {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if (classType.getTypeArguments().isPresent()) {
                List<Type> typeArgs = classType.getTypeArguments().get();
                if (typeArgs.size() == 2) {
                    return Map.of(
                        "type", "map",
                        "keyType", resolveFields(typeArgs.get(0)),
                        "valueType", resolveFields(typeArgs.get(1))
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve map type {}: {}", type, e.getMessage());
        }
        return Map.of("type", "map");
    }

    private boolean isCollectionType(String qualifiedName) {
        return qualifiedName.startsWith("java.util.List") ||
               qualifiedName.startsWith("java.util.Set") ||
               qualifiedName.startsWith("java.util.Collection");
    }

    private Map<String, Object> resolveCollectionType(Type type) {
        try {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if (classType.getTypeArguments().isPresent()) {
                Type genericType = classType.getTypeArguments().get().get(0);
                Map<String, Object> elementType = resolveFields(genericType);
                return Map.of(
                    "type", "array",
                    "items", elementType
                );
            }
        } catch (Exception e) {
            log.warn("Could not resolve collection type {}: {}", type, e.getMessage());
        }
        return Map.of("type", "array", "items", Map.of("type", "Object"));
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
            String fieldName = field.getVariable(0).getNameAsString();
            Type fieldType = field.getVariable(0).getType();
            
            try {
                ResolvedType resolvedType = fieldType.resolve();
                String qualifiedName = resolvedType.describe();

                boolean isJpaRelation = field.getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().matches("OneToMany|ManyToOne|OneToOne|ManyToMany"));

                if (isJpaRelation) {
                    fields.put(fieldName, Map.of("type", "relation"));
                } else if (isCollectionType(qualifiedName)) {
                    fields.put(fieldName, resolveCollectionType(fieldType));
                } else if (isJavaType(qualifiedName)) {
                    fields.put(fieldName, normalizeType(resolvedType.describe()));
                } else {
                    fields.put(fieldName, resolveFields(fieldType));
                }
            } catch (Exception e) {
                fields.put(fieldName, normalizeType(fieldType.asString()));
            }
        }
        
        return fields;
    }
} 
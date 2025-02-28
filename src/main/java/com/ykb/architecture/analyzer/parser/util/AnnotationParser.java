package com.ykb.architecture.analyzer.parser.util;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@UtilityClass
public class AnnotationParser {

    public boolean hasAnnotation(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotationByName(annotationName).isPresent();
    }

    public Optional<String> getAnnotationValue(NodeWithAnnotations<?> node, String annotationName, String paramName) {
        Optional<AnnotationExpr> annotation = node.getAnnotationByName(annotationName);
        log.debug("Looking for annotation {}, found: {}", annotationName, annotation.isPresent());
        
        return annotation.flatMap(ann -> {
            log.debug("Annotation type: {}", ann.getClass().getSimpleName());
            
            if (ann.isSingleMemberAnnotationExpr()) {
                log.debug("Processing SingleMemberAnnotationExpr");
                if (paramName.equals("value") || paramName.equals("path")) {
                    String value = cleanAnnotationValue(ann.asSingleMemberAnnotationExpr().getMemberValue().toString());
                    log.debug("Single member value: {}", value);
                    return Optional.of(value);
                }
                return Optional.empty();
            } else if (ann.isNormalAnnotationExpr()) {
                log.debug("Processing NormalAnnotationExpr");
                // First try the requested parameter name
                Optional<String> value = ann.asNormalAnnotationExpr()
                        .getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals(paramName))
                        .findFirst()
                        .map(pair -> {
                            String val = cleanAnnotationValue(pair.getValue().toString());
                            log.debug("Normal annotation value for {}: {}", paramName, val);
                            return val;
                        });

                // If not found and looking for value, try path
                if (value.isEmpty() && paramName.equals("value")) {
                    value = ann.asNormalAnnotationExpr()
                            .getPairs().stream()
                            .filter(pair -> pair.getNameAsString().equals("path"))
                            .findFirst()
                            .map(pair -> {
                                String val = cleanAnnotationValue(pair.getValue().toString());
                                log.debug("Found path attribute instead of value: {}", val);
                                return val;
                            });
                }
                // If not found and looking for path, try value
                else if (value.isEmpty() && paramName.equals("path")) {
                    value = ann.asNormalAnnotationExpr()
                            .getPairs().stream()
                            .filter(pair -> pair.getNameAsString().equals("value"))
                            .findFirst()
                            .map(pair -> {
                                String val = cleanAnnotationValue(pair.getValue().toString());
                                log.debug("Found value attribute instead of path: {}", val);
                                return val;
                            });
                }
                return value;
            }
            log.debug("Annotation is neither SingleMember nor Normal type");
            return Optional.empty();
        });
    }

    public Optional<String> getAnnotationSingleValue(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotationByName(annotationName)
                .map(annotation -> {
                    if (annotation.isSingleMemberAnnotationExpr()) {
                        return cleanAnnotationValue(annotation.asSingleMemberAnnotationExpr().getMemberValue().toString());
                    } else if (annotation.isNormalAnnotationExpr()) {
                        // Try to get 'value' or 'path' parameter
                        return annotation.asNormalAnnotationExpr()
                                .getPairs().stream()
                                .filter(pair -> pair.getNameAsString().equals("value") || 
                                              pair.getNameAsString().equals("path"))
                                .findFirst()
                                .map(pair -> cleanAnnotationValue(pair.getValue().toString()))
                                .orElse(null);
                    }
                    return null;
                });
    }

    private String cleanAnnotationValue(String value) {
        return value.replaceAll("^\"", "")  // Remove leading quote
                   .replaceAll("\"$", "")   // Remove trailing quote
                   .trim();
    }
}
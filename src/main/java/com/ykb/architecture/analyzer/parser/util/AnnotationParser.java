package com.ykb.architecture.analyzer.parser.util;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@UtilityClass
public class AnnotationParser {

    public static boolean hasAnnotation(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotationByName(annotationName).isPresent();
    }

    public static Optional<String> getAnnotationValue(NodeWithAnnotations<?> node, String annotationName, String attributeName) {
        return node.getAnnotationByName(annotationName)
                .filter(a -> a instanceof NormalAnnotationExpr)
                .map(a -> (NormalAnnotationExpr) a)
                .flatMap(a -> a.getPairs().stream()
                        .filter(p -> p.getNameAsString().equals(attributeName))
                        .findFirst()
                        .map(p -> removeQuotes(p.getValue().toString())));
    }

    public static Optional<String> getAnnotationValue(AnnotationExpr annotation, String attributeName) {
        if (annotation instanceof NormalAnnotationExpr) {
            return ((NormalAnnotationExpr) annotation).getPairs().stream()
                    .filter(p -> p.getNameAsString().equals(attributeName))
                    .findFirst()
                    .map(p -> removeQuotes(p.getValue().toString()));
        }
        return Optional.empty();
    }

    public static Optional<String> getAnnotationSingleValue(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotationByName(annotationName)
                .map(a -> {
                    if (a instanceof SingleMemberAnnotationExpr) {
                        return removeQuotes(((SingleMemberAnnotationExpr) a).getMemberValue().toString());
                    } else if (a instanceof NormalAnnotationExpr) {
                        return ((NormalAnnotationExpr) a).getPairs().stream()
                                .filter(p -> p.getNameAsString().equals("value"))
                                .findFirst()
                                .map(p -> removeQuotes(p.getValue().toString()))
                                .orElse(null);
                    }
                    return null;
                });
    }

    public static Optional<String> getAnnotationSingleValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            return Optional.of(removeQuotes(((SingleMemberAnnotationExpr) annotation).getMemberValue().toString()));
        } else if (annotation instanceof NormalAnnotationExpr) {
            return ((NormalAnnotationExpr) annotation).getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .findFirst()
                    .map(p -> removeQuotes(p.getValue().toString()));
        }
        return Optional.empty();
    }

    private static String removeQuotes(String value) {
        return value.replaceAll("^\"|\"$", "");
    }
}
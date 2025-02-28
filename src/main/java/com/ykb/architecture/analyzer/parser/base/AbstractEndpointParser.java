package com.ykb.architecture.analyzer.parser.base;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class AbstractEndpointParser<T> implements ParserStrategy<T> {

    @Override
    public List<T> parse(CompilationUnit compilationUnit) {
        List<T> endpoints = new ArrayList<>();
        
        try {
            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(this::shouldParse)
                    .map(this::parseClass)
                    .forEach(endpoints::add);
        } catch (Exception e) {
            log.error("Error parsing file: {}", 
                compilationUnit.getStorage().map(s -> s.getPath().toString()).orElse("unknown"), e);
        }
        
        return endpoints;
    }

    @Override
    public boolean canParse(CompilationUnit compilationUnit) {
        return compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(this::shouldParse);
    }

    protected abstract boolean shouldParse(ClassOrInterfaceDeclaration classDeclaration);
    
    protected abstract T parseClass(ClassOrInterfaceDeclaration classDeclaration);
} 
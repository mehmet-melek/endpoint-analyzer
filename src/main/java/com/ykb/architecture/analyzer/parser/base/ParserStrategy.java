package com.ykb.architecture.analyzer.parser.base;

import com.github.javaparser.ast.CompilationUnit;
import com.ykb.architecture.analyzer.core.model.endpoint.BaseEndpoint;

import java.util.List;

public interface ParserStrategy {
    boolean canParse(CompilationUnit compilationUnit);
    List<? extends BaseEndpoint> parse(CompilationUnit compilationUnit);
} 
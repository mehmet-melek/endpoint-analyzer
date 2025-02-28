package com.ykb.architecture.analyzer.parser.base;

import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

public interface ParserStrategy<T> {
    List<T> parse(CompilationUnit compilationUnit);
    boolean canParse(CompilationUnit compilationUnit);
} 
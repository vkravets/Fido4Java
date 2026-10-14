package org.fidonet.gradle;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Applies JFtn expression alignment after Eclipse formatting, without resolving types. */
public final class JavaLayoutFormatter {
    private JavaLayoutFormatter() {
    }

    private record Edit(int start, int end, String text) {
    }

    public static String format(String source) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Java formatting requires a JDK, not a JRE.");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileObject input = new SimpleJavaFileObject(URI.create("string:///Formatting.java"),
                                                        JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var task = (JavacTask) compiler.getTask(null,
                                                    manager,
                                                    diagnostics,
                                                    List.of("-proc:none",
                                                            "-XDallowStringFolding=false"),
                                                    null,
                                                    List.of(input));
            var trees = Trees.instance(task);
            var unit = task.parse().iterator().next();
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IllegalArgumentException(diagnostic.toString());
                }
            }
            String newline = source.contains("\r\n") ? "\r\n" : "\n";
            List<Edit> edits = new ArrayList<>();
            new TreeScanner<Void, Void>() {
                int start(Tree tree) {
                    return (int) trees.getSourcePositions().getStartPosition(unit, tree);
                }
                int end(Tree tree) {
                    return (int) trees.getSourcePositions().getEndPosition(unit, tree);
                }
                String text(Tree tree) {
                    return source.substring(start(tree), end(tree));
                }
                String flat(String text) {
                    return text.strip().replaceAll("[ \\t]*\\R[ \\t]*", " ");
                }
                void operands(ExpressionTree expression, List<ExpressionTree> result) {
                    if (expression instanceof BinaryTree binary
                            && binary.getKind() == Tree.Kind.PLUS) {
                        operands(binary.getLeftOperand(), result);
                        operands(binary.getRightOperand(), result);
                    } else {
                        result.add(expression);
                    }
                }
                String render(ExpressionTree operand, int column) {
                    String original = text(operand);
                    if (!(operand instanceof MethodInvocationTree invocation))
                        return flat(original);
                    List<MethodInvocationTree> chain = new ArrayList<>();
                    MethodInvocationTree current = invocation;
                    ExpressionTree base;
                    while (true) {
                        if (!(current.getMethodSelect() instanceof MemberSelectTree select)) {
                            base = current;
                            break;
                        }
                        chain.add(current);
                        if (!(select.getExpression() instanceof MethodInvocationTree previous)) {
                            base = select.getExpression();
                            break;
                        }
                        current = previous;
                    }
                    if (chain.size() < 2)
                        return flat(original);
                    Collections.reverse(chain);
                    String receiver = flat(text(base));
                    StringBuilder result = new StringBuilder(receiver);
                    String indent = " ".repeat(column + receiver.length());
                    for (int index = 0; index < chain.size(); index++) {
                        MethodInvocationTree call = chain.get(index);
                        MemberSelectTree select = (MemberSelectTree) call.getMethodSelect();
                        String suffix = flat(source.substring(end(select.getExpression()),
                                                              end(call)));
                        if (index > 0)
                            result.append(newline).append(indent);
                        result.append(suffix);
                    }
                    return result.toString();
                }
                void align(List<? extends ExpressionTree> arguments) {
                    align(arguments, true);
                }
                void align(List<? extends ExpressionTree> arguments, boolean methodArguments) {
                    for (int argumentIndex = 0; argumentIndex < arguments.size(); argumentIndex++) {
                        ExpressionTree argument = arguments.get(argumentIndex);
                        if (!(argument instanceof BinaryTree binary)
                                || binary.getKind() != Tree.Kind.PLUS)
                            continue;
                        String original = text(argument);
                        if (!original.contains("\n"))
                            continue;
                        List<ExpressionTree> parts = new ArrayList<>();
                        operands(argument, parts);
                        boolean hasString = parts.stream()
                                                 .anyMatch(part -> part instanceof LiteralTree literal
                                                         && literal.getValue() instanceof String);
                        if (!hasString)
                            continue;
                        if (original.contains("//") || original.contains("/*")
                                || original.contains("\"\"\"") || original.contains("->")) {
                            continue;
                        }
                        int from = start(argument);
                        int expressionEnd = end(argument);
                        // Keep the first argument beside the method name when it fits.
                        if (methodArguments && argumentIndex == 0) {
                            int before = from;
                            while (before > 0 && Character.isWhitespace(source.charAt(before - 1)))
                                before--;
                            if (before > 0 && source.charAt(before - 1) == '(')
                                from = before;
                        }
                        int column = from - (source.lastIndexOf('\n', from - 1) + 1);
                        StringBuilder replacement = new StringBuilder(render(parts.get(0), column));
                        String indent = " ".repeat(column);
                        for (int index = 1; index < parts.size(); index++) {
                            replacement.append(newline)
                                       .append(indent)
                                       .append("+ ")
                                       .append(render(parts.get(index), column + 2));
                        }
                        String result = replacement.toString();
                        String prefix = source.substring(source.lastIndexOf('\n', from - 1) + 1,
                                                         from);
                        int suffixEnd = source.indexOf('\n', expressionEnd);
                        if (suffixEnd < 0)
                            suffixEnd = source.length();
                        String suffix = source.substring(expressionEnd, suffixEnd).stripTrailing();
                        String complete = prefix + result + suffix;
                        if (complete.lines().anyMatch(line -> line.length() > 100)) {
                            continue;
                        }
                        if (!source.substring(from, expressionEnd).equals(result))
                            edits.add(new Edit(from, expressionEnd, result));
                    }
                }
                @Override
                public Void visitMethodInvocation(MethodInvocationTree invocation, Void ignored) {
                    align(invocation.getArguments());
                    return super.visitMethodInvocation(invocation, ignored);
                }
                @Override
                public Void visitNewClass(NewClassTree constructor, Void ignored) {
                    align(constructor.getArguments());
                    return super.visitNewClass(constructor, ignored);
                }
                @Override
                public Void visitVariable(VariableTree variable, Void ignored) {
                    if (variable.getInitializer() != null)
                        align(List.of(variable.getInitializer()), false);
                    return super.visitVariable(variable, ignored);
                }
                @Override
                public Void visitReturn(ReturnTree statement, Void ignored) {
                    if (statement.getExpression() != null)
                        align(List.of(statement.getExpression()), false);
                    return super.visitReturn(statement, ignored);
                }
                @Override
                public Void visitAssignment(AssignmentTree assignment, Void ignored) {
                    align(List.of(assignment.getExpression()), false);
                    return super.visitAssignment(assignment, ignored);
                }
            }.scan(unit, null);
            edits.sort(Comparator.comparingInt(Edit::start));
            List<Edit> disjoint = new ArrayList<>();
            for (Edit edit : edits) {
                if (disjoint.isEmpty() || edit.start() >= disjoint.get(disjoint.size() - 1).end()) {
                    disjoint.add(edit);
                }
            }
            StringBuilder result = new StringBuilder(source);
            for (int index = disjoint.size() - 1; index >= 0; index--) {
                Edit edit = disjoint.get(index);
                result.replace(edit.start(), edit.end(), edit.text());
            }
            if (!source.replaceAll("\\s", "").equals(result.toString().replaceAll("\\s", ""))) {
                throw new IllegalStateException("Expression alignment changed non-whitespace content.");
            }
            return result.toString();
        }
    }
}

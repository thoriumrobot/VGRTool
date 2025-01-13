import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Objects;
import java.util.Set;

/**
 * A refactoring that inserts a direct null-check (e.g. {@code Objects.requireNonNull(expr)})
 * immediately before a flagged dereference, but ONLY IF an "indirect" check is already
 * present in the enclosing scope.
 *
 * <p>
 * For example, if we see:
 *
 * <pre>{@code
 * if (handlerType != null) {
 *     handlerMethod.getBean(); // Potential NullAway warning
 * }
 * }</pre>
 *
 * we add:
 *
 * <pre>{@code
 * if (handlerType != null) {
 *     Objects.requireNonNull(handlerMethod);
 *     handlerMethod.getBean();
 * }
 * }</pre>
 *
 * This clarifies for static analyzers that "handlerMethod" is also guaranteed non-null in
 * that code path without altering real runtime behavior.
 * </p>
 *
 * <p>
 * If NO indirect check is present (i.e. no {@code if (expr != null)} or
 * {@code Assert.state(expr != null, ...)}) in scope, we do NOTHING. This avoids
 * overly aggressive transformations like:
 *
 * <pre>{@code
 * if (this != null) {
 *     this.expectedSize = expectedSize;
 * } else {
 *     throw new NullPointerException();
 * }
 * }</pre>
 *
 * </p>
 */
public class AddNullCheckBeforeDereferenceRefactoring extends Refactoring {

    private final Set<Expression> expressionsPossiblyNull;

    /**
     * @param expressionsPossiblyNull Set of expressions flagged by the verifier as potentially null.
     */
    public AddNullCheckBeforeDereferenceRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    /**
     * Determines if we might apply this refactoring to the given AST node.
     * We only consider:
     *   1) A node that is a dereference (MethodInvocation, FieldAccess, QualifiedName)
     *   2) The qualifier is in {@code expressionsPossiblyNull}.
     */
    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName) {

            Expression qualifier = getQualifier(node);
            return (qualifier != null && expressionsPossiblyNull.contains(qualifier));
        }
        return false;
    }

    /**
     * Applies the refactoring:
     * <ul>
     *   <li>If an indirect check is already present for the qualifier, insert
     *       <code>Objects.requireNonNull(qualifier);</code> just before the original
     *       dereference statement.</li>
     *   <li>If NO indirect check is found, do nothing (no-op).</li>
     * </ul>
     */
    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        Expression qualifier = getQualifier(node);
        if (qualifier == null) {
            return;
        }

        // Get the statement in which the dereference occurs
        Statement enclosingStatement = getEnclosingStatement(node);
        if (enclosingStatement == null) {
            return;
        }

        boolean hasIndirectCheck = hasEnclosingIndirectCheck(qualifier, node);
        if (!hasIndirectCheck) {
            // The user explicitly wants only direct checks in code already guaranteed non-null.
            // If no indirect check is found, we skip the transformation entirely.
            return;
        }

        // If an indirect check is found, insert a direct check before the statement:
        //     Objects.requireNonNull(expr);
        AST ast = node.getAST();

        MethodInvocation requireNonNullCall = ast.newMethodInvocation();
        requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
        requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
        requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, qualifier));

        ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

        // Create a new Block that has the direct check + the original statement
        Block newBlock = ast.newBlock();
        newBlock.statements().add(requireNonNullStmt);
        newBlock.statements().add(ASTNode.copySubtree(ast, enclosingStatement));

        // Replace the original statement with our block
        rewriter.replace(enclosingStatement, newBlock, null);
    }

    /* ==================== Helper Methods ===================== */

    /**
     * Returns the "qualifier" of a dereference:
     *   - For MethodInvocation => .getExpression()
     *   - For FieldAccess => .getExpression()
     *   - For QualifiedName => .getQualifier()
     */
    private Expression getQualifier(ASTNode node) {
        if (node instanceof MethodInvocation) {
            return ((MethodInvocation) node).getExpression();
        }
        if (node instanceof FieldAccess) {
            return ((FieldAccess) node).getExpression();
        }
        if (node instanceof QualifiedName) {
            return ((QualifiedName) node).getQualifier();
        }
        return null;
    }

    /**
     * Gets the nearest enclosing Statement so we can insert code right before it.
     */
    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }

    /**
     * Checks if an "indirect" null-check for 'expr' exists in the enclosing scope. That is,
     * some condition ensures 'expr' is non-null. For instance:
     *   if (expr != null) { ... expr.foo() ... }
     * or
     *   Assert.state(expr != null, "message");
     */
    private boolean hasEnclosingIndirectCheck(Expression expr, ASTNode node) {
        ASTNode current = node.getParent();
        String exprString = expr.toString();

        while (current != null) {
            // if (expr != null) { ... }
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression condition = ifStmt.getExpression();
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        // Match "expr != null" or "null != expr"
                        if ((left.equals(exprString) && "null".equals(right))
                                || ("null".equals(left) && right.equals(exprString))) {
                            return true;
                        }
                    }
                }
            }
            // Assert.state(expr != null, "...")
            if (current instanceof ExpressionStatement) {
                Expression statementExpr = ((ExpressionStatement) current).getExpression();
                if (statementExpr instanceof MethodInvocation) {
                    MethodInvocation mi = (MethodInvocation) statementExpr;
                    if ("state".equals(mi.getName().toString()) && mi.arguments().size() > 0) {
                        Expression firstArg = (Expression) mi.arguments().get(0);
                        // Looking for something like: Assert.state(expr != null, "...")
                        if (firstArg instanceof InfixExpression) {
                            InfixExpression infix = (InfixExpression) firstArg;
                            if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                                String left = infix.getLeftOperand().toString();
                                String right = infix.getRightOperand().toString();
                                if ((left.equals(exprString) && "null".equals(right))
                                        || ("null".equals(left) && right.equals(exprString))) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            current = current.getParent();
        }
        return false;
    }
}

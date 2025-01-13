import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Objects;
import java.util.Set;

/**
 * A refactoring that adds {@code Objects.requireNonNull(expr)} before a flagged
 * dereference, but ONLY IF an "indirect" condition or assertion in the enclosing
 * scope guarantees {@code expr} is non-null.
 *
 * <p>
 * "Indirect" means the condition is not the literal check "expr != null".
 * For instance:
 * <ul>
 *   <li>{@code if (someOtherVar != null) {... expr.foo() ...}}</li>
 *   <li>{@code Assert.state(someOtherVar != null, "...") // ensures expr is non-null}</li>
 * </ul>
 * </p>
 *
 * <p>
 * If the enclosing code is a <em>direct</em> check of {@code expr != null}, we skip
 * insertion to avoid meaningless duplication. For instance:
 *
 * <pre>{@code
 *   if (this.aspectJAdvisorsBuilder != null) {
 *       // do something with this.aspectJAdvisorsBuilder
 *   }
 * }</pre>
 *
 * remains unchanged, because it already explicitly checks {@code this.aspectJAdvisorsBuilder != null}.
 * </p>
 */
public class AddNullCheckBeforeDereferenceRefactoring extends Refactoring {

    private final Set<Expression> expressionsPossiblyNull;

    /**
     * @param expressionsPossiblyNull the set of expressions flagged by the verifier
     *                                as potentially null (from your collected warnings).
     */
    public AddNullCheckBeforeDereferenceRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    /**
     * Checks if the node is a dereference (MethodInvocation, FieldAccess, or QualifiedName)
     * and if the qualifier is flagged by your verifier as possibly null.
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
     * Applies the refactoring. We do the following:
     * <ul>
     *   <li>Find the enclosing statement where the dereference occurs.</li>
     *   <li>Check if there's an <em>indirect</em> condition guaranteeing non-null
     *       (i.e., NOT "expr != null").</li>
     *   <li>If found, insert {@code Objects.requireNonNull(expr);} before the statement.</li>
     *   <li>If no <em>indirect</em> check is found, do nothing (no-op).</li>
     * </ul>
     */
    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        Expression qualifier = getQualifier(node);
        if (qualifier == null) {
            return;
        }

        Statement enclosingStatement = getEnclosingStatement(node);
        if (enclosingStatement == null) {
            return;
        }

        // Only proceed if there's an "indirect" guarantee that expr != null.
        boolean hasIndirectCheck = hasEnclosingIndirectCheck(qualifier, node);
        if (!hasIndirectCheck) {
            // If the check is direct or absent, do nothing.
            return;
        }

        // Insert a direct check:
        //    Objects.requireNonNull(expr);
        // Right before the original statement.
        AST ast = node.getAST();

        MethodInvocation requireNonNullCall = ast.newMethodInvocation();
        requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
        requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
        requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, qualifier));

        ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

        // Build a new block with the direct check + the original statement
        Block newBlock = ast.newBlock();
        newBlock.statements().add(requireNonNullStmt);
        newBlock.statements().add(ASTNode.copySubtree(ast, enclosingStatement));

        // Replace the old statement
        rewriter.replace(enclosingStatement, newBlock, null);
    }

    /* ====================== Helper Methods ====================== */

    /**
     * Returns the AST "qualifier" for a dereference node:
     *   - MethodInvocation => node.getExpression()
     *   - FieldAccess => node.getExpression()
     *   - QualifiedName => node.getQualifier()
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
     * Climbs up the AST until we find the nearest Statement that encloses 'node'.
     */
    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }

    /**
     * Determines whether there's an *indirect* check guaranteeing 'expr' is non-null.
     *
     * <p>An <em>indirect</em> check is something like:
     * <pre>{@code
     * if (someOtherVar != null) {
     *     // => expr is guaranteed non-null
     * }
     * Assert.state(someOtherVar != null, "=> expr is non-null");
     * }</pre>
     *
     * but specifically NOT a direct check "if (expr != null)" or
     * "Assert.state(expr != null, ...)" for the same expression.
     */
    private boolean hasEnclosingIndirectCheck(Expression expr, ASTNode node) {
        ASTNode current = node.getParent();

        // We consider 'expr.toString()' to identify the textual representation of the variable.
        // e.g. "this.aspectJAdvisorsBuilder", "handlerMethod", etc.
        String exprString = expr.toString();

        while (current != null) {

            // CASE A: if (someCond != null) { ... }
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression condition = ifStmt.getExpression();
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();

                        // If the condition is "someCond != null" or "null != someCond", and
                        // 'someCond' is NOT the same as 'exprString', we consider it an *indirect* check.
                        if (isNotNullCheckOfDifferentVar(left, right, exprString)) {
                            return true; 
                        }
                    }
                }
            }

            // CASE B: Assert.state(someCond != null, "...")
            if (current instanceof ExpressionStatement) {
                Expression stmtExpr = ((ExpressionStatement) current).getExpression();
                if (stmtExpr instanceof MethodInvocation) {
                    MethodInvocation mi = (MethodInvocation) stmtExpr;
                    // Looking specifically for "Assert.state(...)"
                    if ("state".equals(mi.getName().getIdentifier()) && mi.arguments().size() > 0) {
                        Expression firstArg = (Expression) mi.arguments().get(0);
                        if (firstArg instanceof InfixExpression) {
                            InfixExpression infix = (InfixExpression) firstArg;
                            if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                                String left = infix.getLeftOperand().toString();
                                String right = infix.getRightOperand().toString();

                                // If "someCond != null" but not literally "expr != null"
                                if (isNotNullCheckOfDifferentVar(left, right, exprString)) {
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

    /**
     * Returns true if the condition is "someVar != null" (or "null != someVar")
     * where "someVar" is NOT the same text as exprString.
     *
     * i.e., if we are checking a *different* variable than 'exprString'.
     */
    private boolean isNotNullCheckOfDifferentVar(String left, String right, String exprString) {
        // Check if either side is "null" and the other side is *someVar* that != exprString
        boolean leftIsNull = "null".equals(left);
        boolean rightIsNull = "null".equals(right);

        // If left is null => right is the variable,
        // If right is null => left is the variable.
        // We only say "true" if the variable is not the same as exprString.
        if (leftIsNull && !right.equals(exprString)) {
            return true;
        }
        if (rightIsNull && !left.equals(exprString)) {
            return true;
        }
        return false;
    }
}

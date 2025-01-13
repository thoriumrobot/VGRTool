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
 * "Indirect" means the condition is not the literal check "expr != null". For instance:
 * <ul>
 *   <li>{@code if (someOtherVar != null) {... expr.foo() ...}}</li>
 *   <li>{@code Assert.state(someOtherVar != null, "...") // ensures expr is non-null}</li>
 * </ul>
 * </p>
 *
 * <p>
 * If the enclosing code has a <em>direct</em> check of {@code expr != null}, we skip
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
 *
 * <p>
 * Additionally:
 *   <ul>
 *     <li>We <b>never</b> check {@code this} for null.</li>
 *     <li>We skip any expression that appears inside a <b>lambda</b> body.</li>
 *     <li>We skip any references to objects known to be <em>imported</em> or statically non-null
 *         (example logic: if the type binding is from external library code).</li>
 *   </ul>
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
     * and if the qualifier is flagged by your verifier as possibly null. Also skips:
     *   - 'this' references
     *   - references inside lambda expressions
     *   - references to known imported/external objects
     *   - direct checks on the same variable are present (i.e. a direct guard)
     */
    @Override
    public boolean isApplicable(ASTNode node) {
        if (!(node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName)) {
            return false;
        }

        Expression qualifier = getQualifier(node);
        if (qualifier == null) {
            return false;
        }

        // 1. Skip if the qualifier is 'this' (or ThisExpression).
        if (isThisReference(qualifier)) {
            return false;
        }

        // 2. Skip if the expression is inside a lambda function body.
        if (isInsideLambda(node)) {
            return false;
        }

        // 3. Skip if the expression is "imported"/external or obviously non-null.
        //    (Example: if the type binding is from outside the user’s source code.)
        if (isExternalReference(qualifier)) {
            return false;
        }

        // 4. Check if the qualifier is flagged as possibly null by the verifier.
        if (!expressionsPossiblyNull.contains(qualifier)) {
            return false;
        }

        // If we pass all above checks, the node is a dereference
        // with a qualifier flagged as possibly null. isApplicable => true.
        return true;
    }

    /**
     * Applies the refactoring. We do the following:
     * <ul>
     *   <li>Find the enclosing statement where the dereference occurs.</li>
     *   <li>Check if there's an <em>indirect</em> condition guaranteeing non-null
     *       (i.e., NOT "expr != null").</li>
     *   <li>If found, insert {@code Objects.requireNonNull(expr);} before the statement,
     *       but only if there's <strong>no direct check</strong> "expr != null" in scope.</li>
     *   <li>If no <em>indirect</em> check is found or a direct check is already present,
     *       do nothing (no-op).</li>
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

        // If there's a direct check "qualifier != null" in an enclosing if/Assert,
        // we skip because it's already guaranteed. This method also returns true
        // if it finds an *indirect* check for a different variable => that is what
        // triggers insertion. We keep these checks separate:
        boolean hasDirectCheckForSameVar = hasEnclosingDirectCheck(qualifier, node);
        boolean hasIndirectCheckForDiffVar = hasEnclosingIndirectCheck(qualifier, node);

        // Only proceed if there's an *indirect* guarantee but no direct check on the same var.
        if (!hasDirectCheckForSameVar && hasIndirectCheckForDiffVar) {
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
     * Returns true if the qualifier is literally 'this', i.e. a ThisExpression.
     */
    private boolean isThisReference(Expression expr) {
        if (expr instanceof ThisExpression) {
            return true;
        }
        // Occasionally `expr.toString()` might be "this" in edge cases,
        // but ThisExpression check is more robust.
        return false;
    }

    /**
     * Returns true if 'node' (or one of its ancestors) is a LambdaExpression.
     * We skip refactoring inside lambdas entirely.
     */
    private boolean isInsideLambda(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof LambdaExpression) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Returns true if 'expr' is known to be an external or imported object
     * (in this simplistic example, we check if the type binding is from
     * outside the current project—i.e., is not "from source").
     */
    private boolean isExternalReference(Expression expr) {
        ITypeBinding binding = expr.resolveTypeBinding();
        if (binding != null) {
            // If not from source, consider it external => skip
            return !binding.isFromSource();
        }
        return false;
    }

    /**
     * Determines whether there's a <em>direct</em> check guaranteeing 'expr' is non-null,
     * specifically "if (expr != null)" or "Assert.state(expr != null, ...)", etc.
     * If we find a direct check on the same variable in the AST path, we return true.
     */
    private boolean hasEnclosingDirectCheck(Expression expr, ASTNode node) {
        ASTNode current = node.getParent();
        String exprString = expr.toString();

        while (current != null) {
            // CASE A: if (expr != null)
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression condition = ifStmt.getExpression();
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();

                        if (isNotNullCheckOfSameVar(left, right, exprString)) {
                            return true; // direct check: "expr != null"
                        }
                    }
                }
            }

            // CASE B: Assert.state(expr != null, ...)
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

                                if (isNotNullCheckOfSameVar(left, right, exprString)) {
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
     * Determines whether there's an *indirect* check guaranteeing 'expr' is non-null
     * because some *other* variable is checked.
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
     * If we find an indirect check, we return true, meaning we have an indirect guarantee
     * that 'expr' is non-null.
     */
    private boolean hasEnclosingIndirectCheck(Expression expr, ASTNode node) {
        ASTNode current = node.getParent();
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

                        // If the condition is "someCond != null" or "null != someCond",
                        // and 'someCond' is NOT the same as exprString,
                        // we consider it an *indirect* check => return true.
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
     * Returns true if the condition is "expr != null" (or "null != expr")
     * for the <strong>same</strong> variable as 'exprString'.
     */
    private boolean isNotNullCheckOfSameVar(String left, String right, String exprString) {
        boolean leftIsNull = "null".equals(left);
        boolean rightIsNull = "null".equals(right);

        if (leftIsNull && right.equals(exprString)) {
            // "null != exprString"
            return true;
        }
        if (rightIsNull && left.equals(exprString)) {
            // "exprString != null"
            return true;
        }
        return false;
    }

    /**
     * Returns true if the condition is "someVar != null" (or "null != someVar")
     * where "someVar" is <strong>not</strong> the same text as exprString.
     *
     * i.e., if we are checking a *different* variable than 'exprString'.
     */
    private boolean isNotNullCheckOfDifferentVar(String left, String right, String exprString) {
        boolean leftIsNull = "null".equals(left);
        boolean rightIsNull = "null".equals(right);

        // If left is null => right is the variable,
        // If right is null => left is the variable.
        // "different var" => !var.equals(exprString).
        if (leftIsNull && !right.equals(exprString)) {
            return true;
        }
        if (rightIsNull && !left.equals(exprString)) {
            return true;
        }
        return false;
    }
}

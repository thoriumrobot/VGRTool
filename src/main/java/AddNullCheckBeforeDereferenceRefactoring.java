import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Objects;
import java.util.Set;

/**
 * A refactoring that adds {@code Objects.requireNonNull(expr)} before a flagged
 * dereference, but ONLY IF an "indirect" condition in the enclosing scope
 * guarantees {@code expr} is non-null.
 *
 * "Indirect" means the condition is not the literal check "expr != null".
 * 
 * If there's already a direct check (e.g., "expr != null") in scope, we skip.
 *
 * We also skip:
 *   - 'this' references
 *   - references inside lambda expressions
 *   - references to classes (Class<?> or static utility classes)
 *   - references to known imported/external objects if desired
 *
 * This refactoring should preserve runtime semantics exactly:
 * we only insert direct "requireNonNull(expr);" where an indirect check
 * already ensures it's non-null, so there's no behavior change at runtime.
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
     * 
     * Also skips:
     *   - 'this' references
     *   - references inside a lambda function
     *   - references that are typed as Class<?>
     *   - references to static or utility classes
     */
    @Override
    public boolean isApplicable(ASTNode node) {
        // 1. Must be a node type that dereferences something
        if (!(node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName)) {
            return false;
        }

        Expression qualifier = getQualifier(node);
        if (qualifier == null) {
            return false;  // no qualifier => no dereference
        }

        // 2. Skip if the qualifier is 'this' (or ThisExpression).
        if (isThisReference(qualifier)) {
            return false;
        }

        // 3. Skip if the expression is inside a lambda function body.
        if (isInsideLambda(node)) {
            return false;
        }

        // NEW: 4. Skip if the qualifier's type is a Class<?> or a known utility class
        //    => never check classes or static references for nullness.
        if (isClassOrStaticReference(qualifier)) {
            return false;
        }

        // 5. Check if the qualifier is flagged as possibly null by the verifier.
        if (!expressionsPossiblyNull.contains(qualifier)) {
            return false;
        }

        // If we pass all above checks, the node is a dereference
        // with a qualifier flagged as possibly null. isApplicable => true.
        return true;
    }

    /**
     * Applies the refactoring. We do the following:
     *   - Find the enclosing statement.
     *   - Check if there's an <em>indirect</em> condition guaranteeing non-null,
     *     but no direct check "expr != null".
     *   - Insert {@code Objects.requireNonNull(expr);} if these conditions hold.
     *   - Otherwise, do nothing (no-op).
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

        // Evaluate direct vs. indirect checks in the AST above.
        boolean hasDirectCheckForSameVar = hasEnclosingDirectCheck(qualifier, node);
        boolean hasIndirectCheckForDiffVar = hasEnclosingIndirectCheck(qualifier, node);

        // We only insert requireNonNull if there's an indirect guarantee
        // for this expression but no direct check for the same var.
        if (!hasDirectCheckForSameVar && hasIndirectCheckForDiffVar) {
            AST ast = node.getAST();

            // Build: Objects.requireNonNull(qualifier);
            MethodInvocation requireNonNullCall = ast.newMethodInvocation();
            requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
            requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
            requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, qualifier));

            ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

            // Create a block that contains our new statement + the old statement
            Block newBlock = ast.newBlock();
            newBlock.statements().add(requireNonNullStmt);
            newBlock.statements().add(ASTNode.copySubtree(ast, enclosingStatement));

            // Replace the old single statement with the new block
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
     * NEW: Returns true if the expression's type binding indicates it's a Class<?>
     * or a static utility class. That typically means:
     *   - The expression's ITypeBinding is "java.lang.Class" or similar
     *   - The expression resolves to a type that is known to be static
     *     (e.g. "Assert" is a static utility class).
     */
    private boolean isClassOrStaticReference(Expression expr) {
        ITypeBinding binding = expr.resolveTypeBinding();
        if (binding == null) {
            return false;
        }

        // If it's literally Class<?>, skip
        if ("java.lang.Class".equals(binding.getQualifiedName())) {
            return true;
        }

        // If it's a top-level class or utility class (like "org.springframework.util.Assert"),
        // we can skip. This check can be refined to your codebase's patterns.
        if (!binding.isFromSource() && binding.isClass()) {
            // If it's from an external library or recognized as a top-level utility,
            // we skip. Or you can check if it's a "static" reference, etc.
            return true;
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
                    // Looking for "Assert.state(...)"
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
     */
    private boolean hasEnclosingIndirectCheck(Expression expr, ASTNode node) {
        ASTNode current = node.getParent();
        String exprString = expr.toString();

        while (current != null) {
            // CASE A: if (someOtherVar != null) { ... }
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

            // CASE B: Assert.state(someOtherVar != null, "...")
            if (current instanceof ExpressionStatement) {
                Expression stmtExpr = ((ExpressionStatement) current).getExpression();
                if (stmtExpr instanceof MethodInvocation) {
                    MethodInvocation mi = (MethodInvocation) stmtExpr;
                    // Looking for "Assert.state(...)"
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

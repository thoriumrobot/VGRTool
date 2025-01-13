import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Objects;
import java.util.Set;

/**
 * A refactoring that ensures code with potential null dereferences is explicitly safe.
 * <p>
 * Functionality:
 *   1. If an "indirect" null check already exists (e.g. {@code if (expr != null)}, 
 *      {@code Assert.state(expr != null, ...)}), then insert a direct check 
 *      ({@code Objects.requireNonNull(expr);}) just before the dereference.
 *   2. If no indirect check exists, wrap the original statement in an 
 *      {@code if (expr != null)} block, and preserve the original "null leads to NPE" 
 *      semantics by throwing explicitly in the else branch.
 *   3. If not a flagged dereference, do nothing (no-op).
 *
 * This helps static analyzers (@Nullable inference tools) see explicit checks,
 * reducing spurious warnings without altering true runtime behavior.
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
     * Determine if we should apply this refactoring to a node:
     *   - Must be a dereference (MethodInvocation, FieldAccess, QualifiedName).
     *   - The qualifier must be in {@code expressionsPossiblyNull}.
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
     *   1) If there's an existing indirect check, insert:
     *        {@code Objects.requireNonNull(expr);}
     *      before the original statement.
     *   2) Otherwise, wrap the statement in:
     *        {@code if (expr != null) { statement } else { throw new NullPointerException(...); }}
     */
    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        Expression qualifier = getQualifier(node);
        if (qualifier == null) {
            return; // no qualifier => cannot proceed
        }

        // The code to transform is the enclosing statement of this dereference
        Statement enclosingStatement = getEnclosingStatement(node);
        if (enclosingStatement == null) {
            return; // no statement => cannot apply
        }

        boolean hasIndirectCheck = hasEnclosingIndirectCheck(qualifier, node);
        AST ast = node.getAST();

        if (hasIndirectCheck) {
            // (1) Insert direct check: Objects.requireNonNull(expr)
            // Build: Objects.requireNonNull(expr);
            MethodInvocation requireNonNullCall = ast.newMethodInvocation();
            requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
            requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
            requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, qualifier));

            ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

            // Build a block containing the direct check + the original statement
            Block block = ast.newBlock();
            block.statements().add(requireNonNullStmt);
            block.statements().add(ASTNode.copySubtree(ast, enclosingStatement));

            // Replace the original statement with our new block
            rewriter.replace(enclosingStatement, block, null);

        } else {
            // (2) No indirect check => wrap the statement in if (expr != null) {...} else { throw ... }
            IfStatement ifStmt = ast.newIfStatement();

            // condition: (expr != null)
            InfixExpression condition = ast.newInfixExpression();
            condition.setLeftOperand((Expression) ASTNode.copySubtree(ast, qualifier));
            condition.setOperator(InfixExpression.Operator.NOT_EQUALS);
            condition.setRightOperand(ast.newNullLiteral());
            ifStmt.setExpression(condition);

            // then block => original statement
            Block thenBlock = ast.newBlock();
            thenBlock.statements().add(ASTNode.copySubtree(ast, enclosingStatement));
            ifStmt.setThenStatement(thenBlock);

            // else block => throw new NullPointerException(...)
            Block elseBlock = ast.newBlock();

            ThrowStatement throwStmt = ast.newThrowStatement();
            ClassInstanceCreation npeCreation = ast.newClassInstanceCreation();
            npeCreation.setType(ast.newSimpleType(ast.newSimpleName("NullPointerException")));
            // You can optionally set a message here if you want to clarify
            // e.g., "NullPointerException(\"expr was null\")"
            throwStmt.setExpression(npeCreation);

            elseBlock.statements().add(throwStmt);
            ifStmt.setElseStatement(elseBlock);

            // Replace the original statement with our if/else
            rewriter.replace(enclosingStatement, ifStmt, null);
        }
    }

    /* ===================== Helper Methods ======================= */

    /**
     * Extract the "qualifier" from a dereference expression:
     *   - For MethodInvocation: .getExpression()
     *   - For FieldAccess: .getExpression()
     *   - For QualifiedName: .getQualifier()
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
     * Finds the Statement that encloses 'node'. We need that to replace or wrap
     * the original statement with new code.
     */
    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }

    /**
     * Determines if there's an "indirect check" guaranteeing non-null for 'expr'
     * in this code path. We do a simple upward traversal to find either:
     *
     *  - An if-statement with (expr != null)
     *  - An Assert.state(expr != null, ...)
     *
     * If we find such a check, we treat the code as already guaranteed safe,
     * meaning we can safely insert just a direct Objects.requireNonNull(...) call.
     */
    private boolean hasEnclosingIndirectCheck(Expression expr, ASTNode node) {
        ASTNode current = node.getParent();
        String exprString = expr.toString();

        while (current != null) {
            // (A) if (expr != null) { ... }
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression condition = ifStmt.getExpression();
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        if ((left.equals(exprString) && "null".equals(right))
                                || (right.equals(exprString) && "null".equals(left))) {
                            return true;
                        }
                    }
                }
            }
            // (B) Assert.state(expr != null, ...)
            if (current instanceof ExpressionStatement) {
                Expression statementExpr = ((ExpressionStatement) current).getExpression();
                if (statementExpr instanceof MethodInvocation) {
                    MethodInvocation mi = (MethodInvocation) statementExpr;
                    if ("state".equals(mi.getName().toString()) && mi.arguments().size() > 0) {
                        // Check first arg for (expr != null)
                        Expression firstArg = (Expression) mi.arguments().get(0);
                        if (firstArg instanceof InfixExpression) {
                            InfixExpression infix = (InfixExpression) firstArg;
                            if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                                String left = infix.getLeftOperand().toString();
                                String right = infix.getRightOperand().toString();
                                if ((left.equals(exprString) && "null".equals(right))
                                        || (right.equals(exprString) && "null".equals(left))) {
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

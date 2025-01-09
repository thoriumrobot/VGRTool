import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.Objects;
import java.util.Set;

/**
 * Refactoring that adds a direct null check (e.g. {@code Objects.requireNonNull(expr)})
 * before dereferences that are already guaranteed safe by some indirect check.
 *
 * <p>This helps static analyzers see a local, explicit assertion that {@code expr} is non-null,
 * improving {@code @Nullable} inference without altering the original runtime behavior.</p>
 *
 * <p>For example:
 * <pre>{@code
 *     if (handlerMethod != null) {
 *         handlerMethod.getBean(); // flagged as possible null by some analyzers
 *     }
 * }
 *
 * becomes
 *
 *     if (handlerMethod != null) {
 *         Objects.requireNonNull(handlerMethod);
 *         handlerMethod.getBean();
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>The semantics remain the same: the new check never throws if the code path
 * was already guaranteed not to be null. But static analyzers now see a
 * direct non-null assertion on {@code handlerMethod} itself.</p>
 */
public class AddNullCheckBeforeDereferenceRefactoring extends Refactoring {

    private final Set<Expression> expressionsPossiblyNull;

    /**
     * @param expressionsPossiblyNull Set of expression nodes that a verifier flagged
     *                                as potentially null (from the warnings).
     */
    public AddNullCheckBeforeDereferenceRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    /**
     * Determines if this refactoring should apply to a given AST node.
     * We only apply if:
     *  1) The node is a dereference (MethodInvocation, FieldAccess, or QualifiedName).
     *  2) The qualifier is in expressionsPossiblyNull.
     *  3) There is an "indirect check" in an enclosing scope that guarantees non-null,
     *     which we detect via hasEnclosingIndirectCheck().
     */
    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName) {

            Expression qualifier = getQualifier(node);
            if (qualifier != null && expressionsPossiblyNull.contains(qualifier)) {
                // Only proceed if there's an indirect check guaranteeing non-null.
                if (hasEnclosingIndirectCheck(qualifier, node)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Applies this refactoring by inserting a direct Objects.requireNonNull(...)
     * statement right before the dereference statement.
     *
     * Example transformation:
     *
     *   Statement:     handlerMethod.getBean();
     *   Replacement:   {
     *       Objects.requireNonNull(handlerMethod);
     *       handlerMethod.getBean();
     *   }
     *
     * This does not change runtime behavior if the code path is only reached
     * when 'handlerMethod != null'.
     */
    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        Expression qualifier = getQualifier(node);
        if (qualifier == null) {
            return;
        }

        // Find the nearest enclosing Statement to insert the check
        Statement enclosingStatement = getEnclosingStatement(node);
        if (enclosingStatement == null) {
            return;
        }

        AST ast = node.getAST();

        // Build an expression: Objects.requireNonNull(qualifier);
        MethodInvocation requireNonNullCall = ast.newMethodInvocation();
        requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
        requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
        requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, qualifier));

        ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

        // Create a new Block to hold the requireNonNull(...) + the original statement
        Block newBlock = ast.newBlock();
        newBlock.statements().add(requireNonNullStmt);
        newBlock.statements().add(ASTNode.copySubtree(ast, enclosingStatement));

        // Replace the original statement with our new block
        rewriter.replace(enclosingStatement, newBlock, null);
    }

    /**
     * Extracts the "qualifier" from a dereference. For example:
     *   - methodCallExpr.getExpression() for MethodInvocation
     *   - fieldAccessExpr.getExpression() for FieldAccess
     *   - qualifiedNameExpr.getQualifier() for QualifiedName
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
     * Finds the nearest enclosing AST node that is a Statement
     * so we can insert our own statement(s) in the same block.
     */
    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }

    /**
     * Checks for an "indirect check" guaranteeing that 'expr' is non-null
     * in the enclosing scope. For example:
     *
     *   if (expr != null) { ... expr.foo() ... }
     *   Assert.state(expr != null, "message");
     *
     * If we detect such a pattern up the AST chain, we conclude that
     * this refactoring is applicable (since adding a direct check won't
     * change runtime behavior).
     *
     * This simple version only looks for:
     *  - if (expr != null) { ... }
     *  - Assert.state(expr != null, ...)
     * (You can expand this logic for more advanced patterns.)
     */
    private boolean hasEnclosingIndirectCheck(Expression expr, ASTNode node) {
        ASTNode current = node.getParent();
        String exprString = expr.toString();

        while (current != null) {
            // (A) Check if there's an if statement with (expr != null)
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression condition = ifStmt.getExpression();
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        // Check (expr != null) or (null != expr)
                        if ((left.equals(exprString) && "null".equals(right))
                                || ("null".equals(left) && right.equals(exprString))) {
                            return true;
                        }
                    }
                }
            }
            // (B) Check for a statement like: Assert.state(expr != null, "...")
            if (current instanceof ExpressionStatement) {
                ExpressionStatement stmt = (ExpressionStatement) current;
                Expression call = stmt.getExpression();
                if (call instanceof MethodInvocation) {
                    MethodInvocation mi = (MethodInvocation) call;
                    String methodName = mi.getName().getIdentifier();
                    // Searching for "Assert.state(...)" pattern
                    if ("state".equals(methodName) && mi.arguments().size() > 0) {
                        Expression firstArg = (Expression) mi.arguments().get(0);
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

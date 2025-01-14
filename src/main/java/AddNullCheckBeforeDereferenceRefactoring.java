import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A refactoring that inserts Objects.requireNonNull(...) calls for variables
 * that are "possibly null" in advanced bridging scenarios. This ensures
 * @Nullable inference tools can see a direct null check whenever the code
 * uses a variable that is only indirectly guaranteed non-null.
 *
 * Constraints:
 *  - Never insert direct checks for 'this'.
 *  - Skip lambda expressions entirely.
 *  - Never add checks for class objects (e.g., if (SomeClass != null))—not relevant.
 *  - Only insert a direct check if there's an *indirect* check in place, i.e.
 *    a bridging scenario (handlerType != null => handlerMethod != null).
 *  - If the same variable is already directly checked in an ancestor `if (x != null)`,
 *    do not add an additional direct check for that same variable.
 */
public class AddNullCheckBeforeDereferenceRefactoring extends Refactoring {

    private final Set<Expression> expressionsPossiblyNull;
    private final Map<String, String> impliesMap;

    public AddNullCheckBeforeDereferenceRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
        this.impliesMap = new HashMap<>();
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        // We are interested in method/field dereferences or return statements
        // Also skip lambdas (LambdaExpression).
        if (node instanceof LambdaExpression) {
            return false;
        }
        // Skip 'this' references or classes
        // (We do a further check in 'apply' for the "this" object.)
        return (node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName
                || node instanceof ReturnStatement);
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        // Build bridging map if we haven't yet
        CompilationUnit cu = getCompilationUnit(node);
        if (cu == null) {
            return;
        }
        buildImplicationsMap(cu);

        // Perform the insertion logic
        if (node instanceof MethodInvocation
            || node instanceof FieldAccess
            || node instanceof QualifiedName) {

            Expression qualifier = getQualifier(node);
            if (qualifier != null) {
                handleDereferenceCase(qualifier, node, rewriter);
            }
        }
        else if (node instanceof ReturnStatement) {
            ReturnStatement rs = (ReturnStatement) node;
            if (rs.getExpression() != null) {
                handleReturnCase(rs.getExpression(), rs, rewriter);
            }
        }
    }

    /* ============================================================
       PHASE 1: Build 'impliesMap' for advanced indirect checks
     ============================================================ */

    @SuppressWarnings("unchecked")
    private void buildImplicationsMap(CompilationUnit cu) {
        cu.accept(new ASTVisitor() {

            @Override
            public boolean visit(VariableDeclarationStatement node) {
                for (Object fragObj : node.fragments()) {
                    if (fragObj instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
                        Expression init = frag.getInitializer();
                        if (init != null) {
                            String assignedVar = frag.getName().getIdentifier();
                            handleInitializer(assignedVar, init);
                        }
                    }
                }
                return super.visit(node);
            }

            @Override
            public boolean visit(VariableDeclarationExpression node) {
                for (Object fragObj : node.fragments()) {
                    if (fragObj instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
                        Expression init = frag.getInitializer();
                        if (init != null) {
                            String assignedVar = frag.getName().getIdentifier();
                            handleInitializer(assignedVar, init);
                        }
                    }
                }
                return super.visit(node);
            }

            @Override
            public boolean visit(Assignment node) {
                // E.g. "bridgeVar = (realVar != null ? ... : null);"
                Expression lhs = node.getLeftHandSide();
                Expression rhs = node.getRightHandSide();
                if (lhs instanceof SimpleName && rhs != null) {
                    String assignedVar = ((SimpleName) lhs).getIdentifier();
                    handleInitializer(assignedVar, rhs);
                }
                return super.visit(node);
            }

            private void handleInitializer(String assignedVar, Expression initExpr) {
                // ConditionalExpression => e.g. assignedVar = (realVar != null ? someValue : null)
                if (initExpr instanceof ConditionalExpression) {
                    ConditionalExpression cond = (ConditionalExpression) initExpr;
                    Expression condition = cond.getExpression();
                    handleCondition(assignedVar, condition);
                }
                // InfixExpression => e.g. assignedVar = (realVar != null && otherStuff)
                else if (initExpr instanceof InfixExpression) {
                    handleInfixExpression(assignedVar, (InfixExpression) initExpr);
                }
            }

            private void handleCondition(String assignedVar, Expression condition) {
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        // if (realVar != null)
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        if (("null".equals(left) && !right.equals("null"))
                                || ("null".equals(right) && !left.equals("null"))) {
                            String nonNullVar = ("null".equals(left)) ? right : left;
                            impliesMap.put(assignedVar, nonNullVar);
                        }
                    }
                }
            }

            private void handleInfixExpression(String assignedVar, InfixExpression infix) {
                if (infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND) {
                    Set<String> nonNullVars = new HashSet<>();
                    collectNotNullVars(infix.getLeftOperand(), nonNullVars);
                    collectNotNullVars(infix.getRightOperand(), nonNullVars);
                    for (Expression e : (java.util.List<Expression>) infix.extendedOperands()) {
                        collectNotNullVars(e, nonNullVars);
                    }
                    for (String varX : nonNullVars) {
                        impliesMap.put(assignedVar, varX);
                    }
                }
            }

            private void collectNotNullVars(Expression expr, Set<String> outVars) {
                if (expr instanceof InfixExpression) {
                    InfixExpression sub = (InfixExpression) expr;
                    if (sub.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = sub.getLeftOperand().toString();
                        String right = sub.getRightOperand().toString();
                        // if (varX != null)
                        if ("null".equals(left) && !right.equals("null")) {
                            outVars.add(right);
                        }
                        else if ("null".equals(right) && !left.equals("null")) {
                            outVars.add(left);
                        }
                    }
                }
            }
        });
    }

    /* ============================================================
       PHASE 2: Insert direct checks if advanced indirect scenario
     ============================================================ */

    private void handleDereferenceCase(Expression qualifier, ASTNode derefNode, ASTRewrite rewriter) {
        // Must be flagged as possibly null
        if (!expressionsPossiblyNull.contains(qualifier)) {
            return;
        }
        // Skip "this" references, e.g. "this.someField"
        if (qualifier instanceof ThisExpression) {
            return;
        }
        // Skip if there's already a direct check on the same var
        if (hasSameVarCheck(qualifier, derefNode)) {
            return;
        }

        Statement enclosingStmt = getEnclosingStatement(derefNode);
        if (enclosingStmt == null) {
            return;
        }

        String qualifierName = qualifier.toString();
        // Check bridging map: e.g. "bridgeVar => realVar"
        for (Map.Entry<String, String> e : impliesMap.entrySet()) {
            String bridgeVar = e.getKey();
            String realVar   = e.getValue();
            if (realVar.equals(qualifierName)) {
                // "bridgeVar => qualifierName"
                if (isInsideNonNullBranch(bridgeVar, derefNode)) {
                    insertObjectsRequireNonNull(qualifier, rewriter, enclosingStmt);
                }
            }
        }
    }

    private void handleReturnCase(Expression returnExpr, ReturnStatement rs, ASTRewrite rewriter) {
        if (!expressionsPossiblyNull.contains(returnExpr)) {
            return;
        }
        // Skip "this" references
        if (returnExpr instanceof ThisExpression) {
            return;
        }
        if (hasSameVarCheck(returnExpr, rs)) {
            return;
        }

        // Possibly a bridging scenario for return statements
        String exprName = returnExpr.toString();
        for (Map.Entry<String, String> e : impliesMap.entrySet()) {
            String bridgeVar = e.getKey();
            String realVar   = e.getValue();
            if (realVar.equals(exprName)) {
                if (isInsideNonNullBranch(bridgeVar, rs)) {
                    insertObjectsRequireNonNull(returnExpr, rewriter, rs);
                }
            }
        }
    }

    /* ============================================================
       HELPER METHODS
     ============================================================ */

    private Expression getQualifier(ASTNode node) {
        if (node instanceof MethodInvocation) {
            return ((MethodInvocation) node).getExpression();
        }
        else if (node instanceof FieldAccess) {
            return ((FieldAccess) node).getExpression();
        }
        else if (node instanceof QualifiedName) {
            return ((QualifiedName) node).getQualifier();
        }
        return null;
    }

    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }

    /**
     * If there's a direct "if (expr != null)" in the ancestor, we skip.
     * This refactoring is only for bridging scenarios, not same-variable checks.
     */
    private boolean hasSameVarCheck(Expression expr, ASTNode node) {
        String exprString = expr.toString();
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof IfStatement) {
                Expression cond = ((IfStatement) current).getExpression();
                if (cond instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) cond;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        if (isNotNullCheckOfSameVar(left, right, exprString)) {
                            return true;
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isNotNullCheckOfSameVar(String left, String right, String exprString) {
        boolean leftIsNull  = "null".equals(left);
        boolean rightIsNull = "null".equals(right);
        // "expr != null" or "null != expr"
        if (leftIsNull && right.equals(exprString)) {
            return true;
        }
        if (rightIsNull && left.equals(exprString)) {
            return true;
        }
        return false;
    }

    /**
     * This is the "magic" fix: we consider that code is in the "non-null" branch of
     * 'bridgeVar' if either:
     *   (A) we are inside `if (bridgeVar != null)` block, or
     *   (B) we are inside the `else` block of `if (bridgeVar == null)`.
     */
    private boolean isInsideNonNullBranch(String bridgeVar, ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression cond = ifStmt.getExpression();
                if (cond instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) cond;
                    String left = infix.getLeftOperand().toString();
                    String right = infix.getRightOperand().toString();

                    // Case A: if (bridgeVar != null)
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        if (isNotNullCheckOfSameVar(left, right, bridgeVar)) {
                            // Are we in the 'then' part?
                            return isDescendantOf(node, ifStmt.getThenStatement());
                        }
                    }
                    // Case B: if (bridgeVar == null) => else => bridgeVar != null
                    if (infix.getOperator() == InfixExpression.Operator.EQUALS) {
                        if (isNullCheckOfSameVar(left, right, bridgeVar)) {
                            // Are we in the 'else' part?
                            return isDescendantOf(node, ifStmt.getElseStatement());
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isNullCheckOfSameVar(String left, String right, String exprString) {
        boolean leftIsNull  = "null".equals(left);
        boolean rightIsNull = "null".equals(right);
        return (leftIsNull && right.equals(exprString))
            || (rightIsNull && left.equals(exprString));
    }

    private boolean isDescendantOf(ASTNode node, Statement stmt) {
        if (stmt == null) {
            return false;
        }
        if (stmt == node) {
            return true;
        }
        final boolean[] found = new boolean[1];
        stmt.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode n) {
                if (n == node) {
                    found[0] = true;
                }
            }
        });
        return found[0];
    }

    private void insertObjectsRequireNonNull(Expression expr, ASTRewrite rewriter, Statement originalStmt) {
        AST ast = originalStmt.getAST();
        MethodInvocation requireNonNullCall = ast.newMethodInvocation();
        requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
        requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
        requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, expr));

        ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

        // Wrap originalStmt in a new Block with the added requireNonNull statement
        Block newBlock = ast.newBlock();
        newBlock.statements().add(requireNonNullStmt);
        newBlock.statements().add(ASTNode.copySubtree(ast, originalStmt));

        rewriter.replace(originalStmt, newBlock, null);
    }

    private CompilationUnit getCompilationUnit(ASTNode node) {
        while (node != null && !(node instanceof CompilationUnit)) {
            node = node.getParent();
        }
        return (CompilationUnit) node;
    }
}

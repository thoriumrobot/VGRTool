import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A refactoring that inserts direct null checks (via Objects.requireNonNull)
 * where a *different* variable's non-null check implies the current variable
 * is also non-null. We skip scenarios where the same variable is checked directly,
 * because we only want the advanced pattern:
 *
 *  Example:
 *     handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);
 *     if (handlerType != null) {
 *         // => handlerMethod != null
 *         // Insert: Objects.requireNonNull(handlerMethod);
 *         handlerMethod.getBean();
 *     }
 *
 * Also handles the "else block" scenario, e.g.:
 *     int pos = (rawValue != null ? rawValue.indexOf(',') : -1);
 *     if (pos == -1) {
 *         return;
 *     }
 *     // else => pos != -1 => rawValue != null
 *     Objects.requireNonNull(rawValue);
 *
 * The bridging relationship is discovered by scanning assignments:
 *   "bridgeVar = (realVar != null ? X : null)"
 * or "bridgeVar = (realVar != null && ...)",
 * stored as "bridgeVar => realVar". We then detect whether the code
 * is in a location that implies "bridgeVar != null => realVar != null"
 * (either in if (bridgeVar != null) or else of if (bridgeVar == null)).
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
        return (node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName
                || node instanceof ReturnStatement);
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        // Build the bridging map (bridgeVar => realVar) by scanning the entire AST
        CompilationUnit cu = getCompilationUnit(node);
        if (cu == null) {
            return;
        }
        buildImplicationsMap(cu);

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
                if (initExpr instanceof ConditionalExpression) {
                    // e.g. assignedVar = (realVar != null ? someValue : null)
                    ConditionalExpression cond = (ConditionalExpression) initExpr;
                    Expression condition = cond.getExpression();
                    handleCondition(assignedVar, condition);
                }
                else if (initExpr instanceof InfixExpression) {
                    // e.g. assignedVar = (realVar != null && otherStuff)
                    handleInfixExpression(assignedVar, (InfixExpression) initExpr);
                }
                // For sentinel numeric checks (pos = rawValue != null ? rawValue.indexOf(...) : -1),
                // you can also store the bridging "pos => rawValue" means "pos != -1 => rawValue != null"
                // if you want. (Not shown in this base version.)
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
        // Skip if there's already a direct check on the same var
        if (hasSameVarCheck(qualifier, derefNode)) {
            return;
        }

        Statement enclosingStmt = getEnclosingStatement(derefNode);
        if (enclosingStmt == null) {
            return;
        }

        String qualifierName = qualifier.toString();
        for (Map.Entry<String, String> e : impliesMap.entrySet()) {
            String bridgeVar = e.getKey();
            String realVar   = e.getValue();
            if (realVar.equals(qualifierName)) {
                // "bridgeVar => qualifierName"
                // => if inside "if (bridgeVar != null)" or the else of "if (bridgeVar == null)",
                // we can safely requireNonNull(qualifier).
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
        if (hasSameVarCheck(returnExpr, rs)) {
            return;
        }

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

    /**
     * Returns the "qualifier" (object being dereferenced) for a
     * MethodInvocation, FieldAccess, or QualifiedName node.
     */
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
     *  (A) we are inside `if (bridgeVar != null)` block, or
     *  (B) we are inside the `else` block of `if (bridgeVar == null)`.
     *
     * That covers patterns like:
     *    if (pos == -1) { return; } else { // => pos != -1 => rawValue != null }
     *
     * If the AST indicates we are in the "else" of `if (bridgeVar == null)`,
     * then it implies `bridgeVar != null` in that branch.
     */
    private boolean isInsideNonNullBranch(String bridgeVar, ASTNode node) {
        // Climb ancestors looking for an IfStatement referencing "bridgeVar"
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
                    // Case B: if (bridgeVar == null), then the 'else' block => bridgeVar != null
                    if (infix.getOperator() == InfixExpression.Operator.EQUALS) {
                        // "if (bridgeVar == null) => else => bridgeVar != null"
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

    /**
     * "bridgeVar == null" or "null == bridgeVar"
     */
    private boolean isNullCheckOfSameVar(String left, String right, String exprString) {
        boolean leftIsNull  = "null".equals(left);
        boolean rightIsNull = "null".equals(right);
        // if (expr == null) or if (null == expr)
        if ((leftIsNull && right.equals(exprString))
             || (rightIsNull && left.equals(exprString))) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if 'node' is inside the subtree of 'stmt' (the 'then' or 'else' block).
     */
    private boolean isDescendantOf(ASTNode node, Statement stmt) {
        if (stmt == null) {
            return false;
        }
        // If the statement is a Block, check if 'node' is within that block
        // or if it's the same statement. An ASTVisitor approach is typical, but
        // here's a quick check:
        if (stmt == node) {
            return true;
        }
        // We'll do a small visitor to see if we find 'node' inside 'stmt'.
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

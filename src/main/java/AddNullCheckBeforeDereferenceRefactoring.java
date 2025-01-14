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
 * This helps a nullability inference tool see the direct null check for 'handlerMethod'
 * in the block, even though the code only checks 'handlerType != null'.
 *
 * Internally, we:
 *  1) Build a map of "bridgeVar => realVar", meaning "bridgeVar != null implies realVar != null".
 *  2) For each dereference (or return) of realVar flagged as possibly null, we see if we're inside
 *     `if (bridgeVar != null)` or an equivalent. If so, we insert `Objects.requireNonNull(realVar)`.
 *  3) We skip the trivial scenario "if (realVar != null) => realVar" by checking for a direct textual match
 *     on the same variable and ignoring it.
 *
 * For sentinel checks (e.g., int pos = rawValue != null ? rawValue.indexOf(',') : -1),
 * you'd extend the logic to treat `pos != -1` => `rawValue != null`. The demonstration below
 * has placeholders for that approach.
 */
public class AddNullChecksForNullableReferencesRefactoring extends Refactoring {

    /**
     * The set of expressions flagged by your verifier as potentially null.
     * This is passed in by your RefactoringEngine, based on the AST scan of warnings, etc.
     */
    private final Set<Expression> expressionsPossiblyNull;

    /**
     * This map records relationships of the form:
     *   "bridgeVar => realVar"
     * meaning "bridgeVar != null" implies "realVar != null".
     *
     * Example:  handlerType => handlerMethod
     */
    private final Map<String, String> impliesMap;

    public AddNullChecksForNullableReferencesRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
        this.impliesMap = new HashMap<>();
    }

    /**
     * We consider a node "applicable" if it's the kind of node where we might insert
     * a direct null check: a dereference (MethodInvocation, FieldAccess, QualifiedName)
     * or a ReturnStatement that returns a variable flagged as null. We then do more
     * specific checks in apply().
     */
    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName
                || node instanceof ReturnStatement) {
            return true;
        }
        return false;
    }

    /**
     * The main logic. We:
     *  1) Build up the "impliesMap" by scanning the entire CompilationUnit for patterns like:
     *       -   varB = (varA != null ? someNonNullExpression : null)
     *       -   varB = (varA != null && someOtherCondition)
     *  2) If the node is a dereference or return, see if it's inside "if (varB != null)" where
     *     "varB => realVar" from the map. If so, we insert `Objects.requireNonNull(realVar)`.
     *  3) Skip if the code directly checks "realVar != null".
     */
    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        // 1) Build the impliesMap for the entire compilation unit (CU)
        CompilationUnit cu = getCompilationUnit(node);
        if (cu == null) {
            return;
        }
        buildImplicationsMap(cu);

        // 2) Depending on the node type, handle the logic
        if (node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName) {

            // This is a dereference node
            Expression qualifier = getQualifier(node);
            if (qualifier != null) {
                handleDereferenceCase(qualifier, node, rewriter);
            }
        }
        else if (node instanceof ReturnStatement) {
            // Possibly handle a return statement: if it's returning a var flagged null,
            // and we see a bridging condition that implies that var is non-null,
            // we insert a direct check before the return.
            ReturnStatement rs = (ReturnStatement) node;
            if (rs.getExpression() != null) {
                handleReturnCase(rs.getExpression(), rs, rewriter);
            }
        }
    }

    /* ===========================================================
       PHASE 1:  Build 'impliesMap' for advanced indirect checks
     =========================================================== */

    /**
     * Scans the entire CompilationUnit for assignment or declaration patterns like:
     *   varB = (varA != null ? someExpression : null);
     * or
     *   varB = (varA != null && somethingElse);
     * which implies "if varB != null => varA != null".
     */
    @SuppressWarnings("unchecked")
    private void buildImplicationsMap(CompilationUnit cu) {
        cu.accept(new ASTVisitor() {

            @Override
            public boolean visit(VariableDeclarationStatement node) {
                // e.g. "Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);"
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
                // For expressions in for-loops, catch same pattern
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
                // e.g. "handlerType = (handlerMethod != null ? ... : null);" outside declarations
                Expression lhs = node.getLeftHandSide();
                Expression rhs = node.getRightHandSide();
                if (lhs instanceof SimpleName && rhs != null) {
                    String assignedVar = ((SimpleName) lhs).getIdentifier();
                    handleInitializer(assignedVar, rhs);
                }
                return super.visit(node);
            }

            private void handleInitializer(String assignedVar, Expression initExpr) {
                // Example patterns:
                //    assignedVar = (condExpr ? nonNullVal : null)
                //    assignedVar = (varA != null && someOtherCheck)
                //    assignedVar = (rawValue != null ? rawValue.indexOf(',') : -1)  // for a sentinel approach
                if (initExpr instanceof ConditionalExpression) {
                    ConditionalExpression cond = (ConditionalExpression) initExpr;
                    Expression condition = cond.getExpression();
                    // if condition is something like (varA != null)
                    handleCondition(assignedVar, condition);
                }
                else if (initExpr instanceof InfixExpression) {
                    // e.g. boolB = (varA != null && isExposeListenerSession());
                    InfixExpression infix = (InfixExpression) initExpr;
                    handleInfixExpression(assignedVar, infix);
                }
                // If you want advanced sentinel logic for numeric checks,
                // you could detect "?: -1" or "?: -999" patterns here. 
            }

            private void handleCondition(String assignedVar, Expression condition) {
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        // if (varA != null)
                        if (("null".equals(left) && !right.equals("null"))
                                || ("null".equals(right) && !left.equals("null"))) {
                            String nonNullVar = ("null".equals(left)) ? right : left;
                            // assignedVar != null => nonNullVar != null
                            impliesMap.put(assignedVar, nonNullVar);
                        }
                    }
                }
            }

            private void handleInfixExpression(String assignedVar, InfixExpression infix) {
                // e.g. assignedVar = (varA != null && someOtherCondition)
                if (infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND) {
                    Set<String> nonNullVars = new HashSet<>();
                    // collect sub-expressions that are "varX != null"
                    collectNotNullVars(infix.getLeftOperand(), nonNullVars);
                    collectNotNullVars(infix.getRightOperand(), nonNullVars);
                    for (Expression extended : (java.util.List<Expression>) infix.extendedOperands()) {
                        collectNotNullVars(extended, nonNullVars);
                    }
                    // For each varX we find, record assignedVar => varX
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
                        // if (left != null)
                        if (("null".equals(left) && !right.equals("null"))) {
                            outVars.add(right);
                        }
                        if (("null".equals(right) && !left.equals("null"))) {
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

    /**
     * Handle a dereference node, e.g. 'methodInvocation', 'fieldAccess', or 'qualifiedName'.
     * Only insert a requireNonNull if we're in a block protected by some bridging var,
     * e.g. if (bridgeVar != null), where impliesMap says "bridgeVar => realVar".
     * We skip the trivial same-variable scenario "if (realVar != null)" => realVar.
     */
    private void handleDereferenceCase(Expression qualifier, ASTNode derefNode, ASTRewrite rewriter) {
        // If your verifier didn't flag it as possibly null, skip
        if (!expressionsPossiblyNull.contains(qualifier)) {
            return;
        }

        // If there's a direct check for the same variable => skip (we only want advanced bridging)
        if (hasSameVarCheck(qualifier, derefNode)) {
            return;
        }

        Statement enclosingStmt = getEnclosingStatement(derefNode);
        if (enclosingStmt == null) {
            return;
        }

        // see if there's "bridgeVar => qualifierVar" in impliesMap
        String qualifierName = qualifier.toString();
        for (Map.Entry<String, String> e : impliesMap.entrySet()) {
            String bridgeVar = e.getKey();
            String realVar = e.getValue();
            if (realVar.equals(qualifierName)) {
                // that means "bridgeVar != null => qualifierName != null"
                // check if we are indeed inside "if (bridgeVar != null)"
                if (isInsideIfCondition(bridgeVar, derefNode)) {
                    // Insert requireNonNull(qualifier) before the statement
                    insertObjectsRequireNonNull(qualifier, rewriter, enclosingStmt);
                }
            }
        }
    }

    /**
     * If you want to insert direct checks before returning a variable flagged as null
     * in advanced bridging scenarios, do it here.
     */
    private void handleReturnCase(Expression returnExpr, ReturnStatement rs, ASTRewrite rewriter) {
        if (!expressionsPossiblyNull.contains(returnExpr)) {
            return;
        }
        if (hasSameVarCheck(returnExpr, rs)) {
            return;
        }

        // check bridging relationships
        String exprName = returnExpr.toString();
        for (Map.Entry<String, String> e : impliesMap.entrySet()) {
            String bridgeVar = e.getKey();
            String realVar = e.getValue();
            if (realVar.equals(exprName)) {
                if (isInsideIfCondition(bridgeVar, rs)) {
                    insertObjectsRequireNonNull(returnExpr, rewriter, rs);
                }
            }
        }
    }

    /* ============================================================
        HELPER METHODS
     ============================================================ */

    /**
     * For a MethodInvocation, FieldAccess, or QualifiedName node, returns
     * the "qualifier" expression (the object being dereferenced).
     * Returns null if there's no qualifier.
     */
    private Expression getQualifier(ASTNode node) {
        if (node instanceof MethodInvocation) {
            return ((MethodInvocation) node).getExpression();
        } else if (node instanceof FieldAccess) {
            return ((FieldAccess) node).getExpression();
        } else if (node instanceof QualifiedName) {
            return ((QualifiedName) node).getQualifier();
        }
        return null;
    }

    /**
     * Climbs the AST until we find the nearest Statement enclosing 'node'.
     */
    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }

    /**
     * Checks if there's a direct check "if (expr != null)" for the same variable 'expr'
     * in an enclosing if-statement. If found, we skip because we want only advanced bridging,
     * not the same-variable scenario.
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
        // "expr != null" or "null != expr"
        boolean leftIsNull = "null".equals(left);
        boolean rightIsNull = "null".equals(right);

        // If left is "null" and right is exprString => "null != exprString"
        // If right is "null" and left is exprString => "exprString != null"
        if (leftIsNull && right.equals(exprString)) {
            return true;
        }
        if (rightIsNull && left.equals(exprString)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if we are inside an if-statement block that says "if (bridgeVar != null)"
     * or an equivalent check. If so, we assume that "bridgeVar != null => realVar != null"
     * from impliesMap is valid in that block.
     */
    private boolean isInsideIfCondition(String bridgeVar, ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression cond = ifStmt.getExpression();
                if (cond instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) cond;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        if (isNotNullCheckOfSameVar(left, right, bridgeVar)) {
                            return true;
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Inserts "Objects.requireNonNull(expr);" immediately before originalStmt
     * inside a new Block, preserving runtime semantics (the code only runs in
     * a scenario where expr is guaranteed not to be null anyway).
     */
    private void insertObjectsRequireNonNull(Expression expr,
                                             ASTRewrite rewriter,
                                             Statement originalStmt) {
        AST ast = originalStmt.getAST();

        // Build: Objects.requireNonNull(expr);
        MethodInvocation requireNonNullCall = ast.newMethodInvocation();
        requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
        requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
        requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, expr));

        ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

        // Wrap in a new block: { Objects.requireNonNull(expr); originalStmt; }
        Block newBlock = ast.newBlock();
        newBlock.statements().add(requireNonNullStmt);
        newBlock.statements().add(ASTNode.copySubtree(ast, originalStmt));

        // Replace the old statement with the new block
        rewriter.replace(originalStmt, newBlock, null);
    }

    /**
     * Utility to climb up to the containing CompilationUnit, if any.
     */
    private CompilationUnit getCompilationUnit(ASTNode node) {
        while (node != null && !(node instanceof CompilationUnit)) {
            node = node.getParent();
        }
        return (CompilationUnit) node;
    }
}

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A refactoring that inserts Objects.requireNonNull(...) calls for variables
 * that are "possibly null" in advanced bridging scenarios.
 */
public class AddNullCheckBeforeDereferenceRefactoring extends Refactoring {

    private final Set<Expression> expressionsPossiblyNull;
    private final Map<String, String> impliesMap;

    // No-argument constructor.
    public AddNullCheckBeforeDereferenceRefactoring() {
        this.expressionsPossiblyNull = new HashSet<>();
        this.impliesMap = new HashMap<>();
    }
    
    // Overloaded constructor to allow passing a set of expressions.
    public AddNullCheckBeforeDereferenceRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
        this.impliesMap = new HashMap<>();
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof LambdaExpression) {
            return false;
        }
        return (node instanceof MethodInvocation
                || node instanceof FieldAccess
                || node instanceof QualifiedName
                || node instanceof ReturnStatement);
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
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
        } else if (node instanceof ReturnStatement) {
            ReturnStatement rs = (ReturnStatement) node;
            if (rs.getExpression() != null) {
                handleReturnCase(rs.getExpression(), rs, rewriter);
            }
        }
    }

    private void buildImplicationsMap(CompilationUnit cu) {
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                processVariableDeclaration(node.fragments());
                return super.visit(node);
            }

            @Override
            public boolean visit(VariableDeclarationExpression node) {
                processVariableDeclaration(node.fragments());
                return super.visit(node);
            }

            @Override
            public boolean visit(Assignment node) {
                Expression lhs = node.getLeftHandSide();
                Expression rhs = node.getRightHandSide();
                if (lhs instanceof SimpleName && rhs != null) {
                    String assignedVar = ((SimpleName) lhs).getIdentifier();
                    handleInitializer(assignedVar, rhs);
                }
                return super.visit(node);
            }

            private void processVariableDeclaration(List fragments) {
                for (Object fragObj : fragments) {
                    if (fragObj instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
                        Expression init = frag.getInitializer();
                        if (init != null) {
                            String assignedVar = frag.getName().getIdentifier();
                            handleInitializer(assignedVar, init);
                        }
                    }
                }
            }

            private void handleInitializer(String assignedVar, Expression initExpr) {
                if (initExpr instanceof ConditionalExpression) {
                    ConditionalExpression cond = (ConditionalExpression) initExpr;
                    handleCondition(assignedVar, cond.getExpression());
                } else if (initExpr instanceof InfixExpression) {
                    handleInfixExpression(assignedVar, (InfixExpression) initExpr);
                }
            }

            private void handleCondition(String assignedVar, Expression condition) {
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = infix.getLeftOperand().toString();
                        String right = infix.getRightOperand().toString();
                        if (isNotNullComparison(left, right)) {
                            String nonNullVar = "null".equals(left) ? right : left;
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
                    for (String var : nonNullVars) {
                        impliesMap.put(assignedVar, var);
                    }
                }
            }

            private void collectNotNullVars(Expression expr, Set<String> outVars) {
                if (expr instanceof InfixExpression) {
                    InfixExpression sub = (InfixExpression) expr;
                    if (sub.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        String left = sub.getLeftOperand().toString();
                        String right = sub.getRightOperand().toString();
                        if (isNotNullComparison(left, right)) {
                            outVars.add("null".equals(left) ? right : left);
                        }
                    }
                }
            }
        });
    }

    private void handleDereferenceCase(Expression qualifier, ASTNode derefNode, ASTRewrite rewriter) {
        if (!expressionsPossiblyNull.contains(qualifier)) {
            return;
        }
        if (qualifier instanceof ThisExpression) {
            return;
        }
        if (hasSameVarCheck(qualifier, derefNode)) {
            return;
        }

        String qualifierName = qualifier.toString();
        for (Map.Entry<String, String> entry : impliesMap.entrySet()) {
            String bridgeVar = entry.getKey();
            String realVar = entry.getValue();
            if (realVar.equals(qualifierName) && isInsideNonNullBranch(bridgeVar, derefNode)) {
                insertObjectsRequireNonNull(qualifier, rewriter, getEnclosingStatement(derefNode));
            }
        }
    }

    private void handleReturnCase(Expression returnExpr, ReturnStatement rs, ASTRewrite rewriter) {
        if (!expressionsPossiblyNull.contains(returnExpr)) {
            return;
        }
        if (returnExpr instanceof ThisExpression) {
            return;
        }
        if (hasSameVarCheck(returnExpr, rs)) {
            return;
        }

        String exprName = returnExpr.toString();
        for (Map.Entry<String, String> entry : impliesMap.entrySet()) {
            String bridgeVar = entry.getKey();
            String realVar = entry.getValue();
            if (realVar.equals(exprName) && isInsideNonNullBranch(bridgeVar, rs)) {
                insertObjectsRequireNonNull(returnExpr, rewriter, rs);
            }
        }
    }

    // This version inserts a requireNonNull call before the original statement.
    private void insertObjectsRequireNonNull(Expression expr, ASTRewrite rewriter, Statement originalStmt) {
        if (originalStmt == null) {
            return;
        }
        AST ast = originalStmt.getAST();
        MethodInvocation requireNonNullCall = ast.newMethodInvocation();
        requireNonNullCall.setExpression(ast.newSimpleName("Objects"));
        requireNonNullCall.setName(ast.newSimpleName("requireNonNull"));
        requireNonNullCall.arguments().add(ASTNode.copySubtree(ast, expr));

        ExpressionStatement requireNonNullStmt = ast.newExpressionStatement(requireNonNullCall);

        rewriter.getListRewrite(originalStmt.getParent(), Block.STATEMENTS_PROPERTY)
                .insertBefore(requireNonNullStmt, originalStmt, null);
    }

    // Finds the CompilationUnit for the given node.
    private CompilationUnit getCompilationUnit(ASTNode node) {
        while (node != null && !(node instanceof CompilationUnit)) {
            node = node.getParent();
        }
        return (CompilationUnit) node;
    }
    
    // Returns the qualifier of the node if applicable.
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

    // Retrieves the enclosing Statement for the given node.
    private Statement getEnclosingStatement(ASTNode node) {
        while (node != null && !(node instanceof Statement)) {
            node = node.getParent();
        }
        return (Statement) node;
    }

    private boolean hasSameVarCheck(Expression expr, ASTNode node) {
        String exprString = expr.toString();
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof IfStatement) {
                Expression condition = ((IfStatement) current).getExpression();
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS) {
                        if (isNotNullComparison(infix.getLeftOperand().toString(), infix.getRightOperand().toString(), exprString)) {
                            return true;
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isNotNullComparison(String left, String right) {
        return ("null".equals(left) && !right.equals("null"))
                || ("null".equals(right) && !left.equals("null"));
    }

    private boolean isNotNullComparison(String left, String right, String exprString) {
        return ("null".equals(left) && right.equals(exprString))
                || ("null".equals(right) && left.equals(exprString));
    }

    private boolean isInsideNonNullBranch(String bridgeVar, ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) current;
                Expression condition = ifStmt.getExpression();
                if (condition instanceof InfixExpression) {
                    InfixExpression infix = (InfixExpression) condition;
                    if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
                            && isNotNullComparison(infix.getLeftOperand().toString(), infix.getRightOperand().toString(), bridgeVar)) {
                        return true;
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
}


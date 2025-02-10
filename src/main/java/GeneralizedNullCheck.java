import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class GeneralizedNullCheck extends Refactoring {
    private List<Expression> expressionsPossiblyNull;
    private final Map<String, Expression> variableAssignments = new HashMap<>();

    public GeneralizedNullCheck() {
        super();
    }

    public GeneralizedNullCheck(List<Expression> expressionsPossiblyNull) {
        super();
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) node;
            Expression condition = ifStmt.getExpression();
            if (isIndirectNullCheck(condition)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        if (!(node instanceof IfStatement)) return;

        // Ensure all assignments are mapped
        traverseAST(node.getRoot());

        IfStatement ifStmt = (IfStatement) node;
        Expression condition = ifStmt.getExpression();
        Expression directCheck = resolveIndirectNullCheck(condition, node.getAST());

        if (directCheck != null) {
            rewriter.replace(ifStmt.getExpression(), directCheck, null);
        }
    }

    private boolean isIndirectNullCheck(Expression condition) {
        if (condition instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression) condition;
            if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS &&
                (infix.getRightOperand() instanceof NullLiteral || infix.getLeftOperand() instanceof NullLiteral)) {
                
                SimpleName varName = extractSimpleName(infix);
                return varName != null && variableAssignments.containsKey(varName.getIdentifier());
            }
        }
        return false;
    }

    private Expression resolveIndirectNullCheck(Expression condition, AST ast) {
        if (!(condition instanceof InfixExpression)) return null;
        
        InfixExpression infix = (InfixExpression) condition;
        SimpleName varName = extractSimpleName(infix);
        
        if (varName == null || !variableAssignments.containsKey(varName.getIdentifier())) return null;

        Expression originalAssignment = variableAssignments.get(varName.getIdentifier());
        Expression rootCheck = traceNullSource(originalAssignment, ast);

        return (rootCheck != null) ? rootCheck : condition;
    }

    private SimpleName extractSimpleName(InfixExpression infix) {
        if (infix.getLeftOperand() instanceof SimpleName) {
            return (SimpleName) infix.getLeftOperand();
        } else if (infix.getRightOperand() instanceof SimpleName) {
            return (SimpleName) infix.getRightOperand();
        }
        return null;
    }

    private Expression traceNullSource(Expression expr, AST ast) {
        while (expr instanceof ParenthesizedExpression) {
            expr = ((ParenthesizedExpression) expr).getExpression();
        }

        if (expr instanceof ConditionalExpression) {
            ConditionalExpression conditional = (ConditionalExpression) expr;
            return (Expression) ASTNode.copySubtree(ast, conditional.getExpression());
        }

        if (expr instanceof SimpleName) {
            String refVarName = ((SimpleName) expr).getIdentifier();
            if (variableAssignments.containsKey(refVarName)) {
                return traceNullSource(variableAssignments.get(refVarName), ast);
            }
        }

        return null;
    }

    private void analyzeAssignments(ASTNode node) {
        if (node instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) node;
            variableAssignments.put(fragment.getName().getIdentifier(), fragment.getInitializer());
        } else if (node instanceof Assignment) {
            Assignment assignment = (Assignment) node;
            if (assignment.getLeftHandSide() instanceof SimpleName) {
                SimpleName varName = (SimpleName) assignment.getLeftHandSide();
                variableAssignments.put(varName.getIdentifier(), assignment.getRightHandSide());
            }
        }
    }

    public void traverseAST(ASTNode node) {
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                analyzeAssignments(node);
                return true;
            }

            @Override
            public boolean visit(Assignment node) {
                analyzeAssignments(node);
                return true;
            }
        });
    }
}


import java.util.Set;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class AddNullChecksForNullableReferencesRefactoring extends Refactoring {

    private Set<Expression> expressionsPossiblyNull;

    public AddNullChecksForNullableReferencesRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) node;
            Expression expr = mi.getExpression();
            return (expr != null && expressionsPossiblyNull.contains(expr));
        } else if (node instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess) node;
            Expression expr = fa.getExpression();
            return (expr != null && expressionsPossiblyNull.contains(expr));
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        Statement parentStatement = getEnclosingStatement(node);
        if (parentStatement == null) {
            return;
        }

        AST ast = parentStatement.getAST();
        Expression exprToCheck = null;

        if (node instanceof MethodInvocation) {
            exprToCheck = ((MethodInvocation) node).getExpression();
        } else if (node instanceof FieldAccess) {
            exprToCheck = ((FieldAccess) node).getExpression();
        }

        if (exprToCheck == null) return;

        IfStatement ifStatement = ast.newIfStatement();
        InfixExpression condition = ast.newInfixExpression();
        condition.setOperator(InfixExpression.Operator.NOT_EQUALS);
        condition.setLeftOperand((Expression) rewriter.createCopyTarget(exprToCheck));
        condition.setRightOperand(ast.newNullLiteral());
        ifStatement.setExpression(condition);

        Block thenBlock = ast.newBlock();
        thenBlock.statements().add(rewriter.createCopyTarget(parentStatement));
        ifStatement.setThenStatement(thenBlock);

        rewriter.replace(parentStatement, ifStatement, null);
    }

    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }
}


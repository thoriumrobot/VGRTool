import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Set;

class AddNullCheckBeforeDereferenceRefactoring extends Refactoring {

    private Set<Expression> expressionsPossiblyNull;

    public AddNullCheckBeforeDereferenceRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        // Check if the node is a dereference of a possibly null expression
        if (node instanceof MethodInvocation || node instanceof FieldAccess || node instanceof QualifiedName) {
            Expression expr = getQualifier(node);
            return expr != null && expressionsPossiblyNull.contains(expr);
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        Expression expr = getQualifier(node);
        if (expr == null) {
            return;
        }

        AST ast = node.getAST();
        Statement enclosingStatement = getEnclosingStatement(node);
        if (enclosingStatement == null) {
            return;
        }

        // Create null check: if (expr == null) { /* handle null case */ } else { /* original statement */ }
        IfStatement ifStatement = ast.newIfStatement();
        InfixExpression condition = ast.newInfixExpression();
        condition.setLeftOperand((Expression) ASTNode.copySubtree(ast, expr));
        condition.setOperator(InfixExpression.Operator.EQUALS);
        condition.setRightOperand(ast.newNullLiteral());
        ifStatement.setExpression(condition);

        // Handle the null case
        ifStatement.setThenStatement(ast.newReturnStatement());

        // Original statement goes into the 'else' part
        Block elseBlock = ast.newBlock();
        elseBlock.statements().add(ASTNode.copySubtree(ast, enclosingStatement));
        ifStatement.setElseStatement(elseBlock);

        // Replace the original statement with the if statement
        rewriter.replace(enclosingStatement, ifStatement, null);
    }

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

    private Statement getEnclosingStatement(ASTNode node) {
        while (node != null && !(node instanceof Statement)) {
            node = node.getParent();
        }
        return (Statement) node;
    }
}


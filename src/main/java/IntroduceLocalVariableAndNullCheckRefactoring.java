import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.*;

public class IntroduceLocalVariableAndNullCheckRefactoring extends Refactoring {

    private Set<Expression> expressionsPossiblyNull;

    public IntroduceLocalVariableAndNullCheckRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof MethodInvocation || node instanceof FieldAccess) {
            Expression expr = (Expression) node;
            return expressionsPossiblyNull.contains(expr);
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        AST ast = node.getAST();
        Expression expr = (Expression) node;

        // Find the enclosing block
        Block enclosingBlock = getEnclosingBlock(node);
        if (enclosingBlock == null) {
            return;
        }

        // Introduce a new local variable for the possibly null expression
        String varName = generateVariableName(expr);
        VariableDeclarationStatement varDecl = createVariableDeclaration(ast, varName, expr);

        // Replace occurrences of the expression with the new variable
        ReplaceExpressionVisitor replaceVisitor = new ReplaceExpressionVisitor(expr, varName, rewriter);
        enclosingBlock.accept(replaceVisitor);

        // Insert the variable declaration at the beginning of the block
        ListRewrite listRewrite = rewriter.getListRewrite(enclosingBlock, Block.STATEMENTS_PROPERTY);
        listRewrite.insertFirst(varDecl, null);

        // Wrap dependent code in an if (var != null) block
        IfStatement ifStatement = ast.newIfStatement();
        InfixExpression condition = ast.newInfixExpression();
        condition.setLeftOperand(ast.newSimpleName(varName));
        condition.setOperator(InfixExpression.Operator.NOT_EQUALS);
        condition.setRightOperand(ast.newNullLiteral());
        ifStatement.setExpression(condition);

        // Collect dependent statements
        List<Statement> dependentStatements = getDependentStatements(enclosingBlock, varName);

        // Move statements that depend on the variable into the if block
        Block ifBlock = ast.newBlock();
        for (Statement stmt : dependentStatements) {
            ifBlock.statements().add(ASTNode.copySubtree(ast, stmt));
            rewriter.remove(stmt, null);
        }
        ifStatement.setThenStatement(ifBlock);

        // Insert the if statement after the variable declaration
        listRewrite.insertAfter(ifStatement, varDecl, null);
    }

    // Rest of the helper methods remain the same
}


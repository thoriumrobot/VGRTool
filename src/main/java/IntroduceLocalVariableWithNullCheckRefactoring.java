import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

public class IntroduceLocalVariableWithNullCheckRefactoring extends Refactoring {

    private Set<Expression> nullableExpressions;

    public IntroduceLocalVariableWithNullCheckRefactoring(Set<Expression> nullableExpressions) {
        this.nullableExpressions = nullableExpressions;
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof MethodInvocation || node instanceof FieldAccess) {
            Expression expr = (Expression) node;
            return nullableExpressions.contains(expr);
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

        // Generate variable name
        String varName = generateVariableName(expr);

        // Create variable declaration
        VariableDeclarationFragment varDeclFragment = ast.newVariableDeclarationFragment();
        varDeclFragment.setName(ast.newSimpleName(varName));
        varDeclFragment.setInitializer((Expression) ASTNode.copySubtree(ast, expr));
        VariableDeclarationStatement varDecl = ast.newVariableDeclarationStatement(varDeclFragment);
        varDecl.setType(determineType(ast, expr));

        // Insert variable declaration at the beginning of the block
        ListRewrite listRewrite = rewriter.getListRewrite(enclosingBlock, Block.STATEMENTS_PROPERTY);
        listRewrite.insertFirst(varDecl, null);

        // Replace all occurrences of the expression with the variable
        ReplaceExpressionVisitor replaceVisitor = new ReplaceExpressionVisitor(expr, varName, rewriter);
        enclosingBlock.accept(replaceVisitor);

        // Wrap code that depends on the variable in an if (var != null) block
        IfStatement ifStmt = ast.newIfStatement();
        InfixExpression condition = ast.newInfixExpression();
        condition.setLeftOperand(ast.newSimpleName(varName));
        condition.setOperator(InfixExpression.Operator.NOT_EQUALS);
        condition.setRightOperand(ast.newNullLiteral());
        ifStmt.setExpression(condition);

        // Collect statements that depend on the variable
        List<Statement> dependentStatements = getDependentStatements(enclosingBlock, varName);

        // Create a new block for the if statement
        Block ifBlock = ast.newBlock();
        for (Statement stmt : dependentStatements) {
            ifBlock.statements().add(ASTNode.copySubtree(ast, stmt));
            rewriter.remove(stmt, null);
        }
        ifStmt.setThenStatement(ifBlock);

        // Insert the if statement after the variable declaration
        listRewrite.insertAfter(ifStmt, varDecl, null);
    }

    // Helper methods (getEnclosingBlock, generateVariableName, determineType, getDependentStatements, isVariableDeclaration, usesVariable, UsesVariableVisitor, ReplaceExpressionVisitor) are the same as above
}


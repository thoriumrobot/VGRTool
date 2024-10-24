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

    private Expression getExpression(ASTNode node) {
        if (node instanceof MethodInvocation) {
            return ((MethodInvocation) node).getExpression();
        } else if (node instanceof FieldAccess) {
            return ((FieldAccess) node).getExpression();
        }
        return null;
    }

    private Block getEnclosingBlock(ASTNode node) {
        while (node != null && !(node instanceof Block)) {
            node = node.getParent();
        }
        return (Block) node;
    }

    private String generateVariableName(Expression expr) {
        // Generate a unique variable name based on the expression
        String baseName = "tempVar";
        if (expr instanceof MethodInvocation) {
            baseName = ((MethodInvocation) expr).getName().getIdentifier();
        } else if (expr instanceof SimpleName) {
            baseName = ((SimpleName) expr).getIdentifier();
        }
        return baseName + "_local";
    }

    private VariableDeclarationStatement createVariableDeclaration(AST ast, String varName, Expression expr) {
        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName(varName));
        fragment.setInitializer((Expression) ASTNode.copySubtree(ast, expr));

        VariableDeclarationStatement varDecl = ast.newVariableDeclarationStatement(fragment);
        varDecl.setType(ast.newSimpleType(ast.newSimpleName("Object"))); // Use appropriate type
        return varDecl;
    }

    private List<Statement> getDependentStatements(Block block, String varName) {
        // Collect statements that depend on the variable
        List<Statement> dependentStatements = new ArrayList<>();
        for (Object stmtObj : block.statements()) {
            Statement stmt = (Statement) stmtObj;
            if (usesVariable(stmt, varName)) {
                dependentStatements.add(stmt);
            }
        }
        return dependentStatements;
    }

    private boolean usesVariable(Statement stmt, String varName) {
        UsesVariableVisitor visitor = new UsesVariableVisitor(varName);
        stmt.accept(visitor);
        return visitor.isVariableUsed();
    }

    // Helper class to replace occurrences of the expression with the new variable
    class ReplaceExpressionVisitor extends ASTVisitor {
        private Expression targetExpr;
        private String varName;
        private ASTRewrite rewriter;

        public ReplaceExpressionVisitor(Expression targetExpr, String varName, ASTRewrite rewriter) {
            this.targetExpr = targetExpr;
            this.varName = varName;
            this.rewriter = rewriter;
        }

        @Override
        public void endVisit(MethodInvocation node) {
            if (node.getExpression() != null && node.getExpression().subtreeMatch(new ASTMatcher(), targetExpr)) {
                rewriter.replace(node.getExpression(), node.getAST().newSimpleName(varName), null);
            }
        }

        @Override
        public void endVisit(FieldAccess node) {
            if (node.getExpression().subtreeMatch(new ASTMatcher(), targetExpr)) {
                rewriter.replace(node.getExpression(), node.getAST().newSimpleName(varName), null);
            }
        }
    }

    // Helper class to check if a statement uses the variable
    class UsesVariableVisitor extends ASTVisitor {
        private String varName;
        private boolean variableUsed = false;

        public UsesVariableVisitor(String varName) {
            this.varName = varName;
        }

        @Override
        public void endVisit(SimpleName node) {
            if (node.getIdentifier().equals(varName)) {
                variableUsed = true;
            }
        }

        public boolean isVariableUsed() {
            return variableUsed;
        }
    }
}


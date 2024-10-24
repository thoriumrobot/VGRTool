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

    private Block getEnclosingBlock(ASTNode node) {
        while (node != null && !(node instanceof Block)) {
            node = node.getParent();
        }
        return (Block) node;
    }

    private String generateVariableName(Expression expr) {
        String baseName = "tempVar";
        if (expr instanceof MethodInvocation) {
            baseName = ((MethodInvocation) expr).getName().getIdentifier();
        } else if (expr instanceof FieldAccess) {
            baseName = ((FieldAccess) expr).getName().getIdentifier();
        } else if (expr instanceof SimpleName) {
            baseName = ((SimpleName) expr).getIdentifier();
        }
        return baseName + "_local";
    }

    private Type determineType(AST ast, Expression expr) {
        // Determine the correct type for the variable based on the expression
        // For simplicity, we'll use 'Object' here, but in practice, you should use the actual type
        return ast.newSimpleType(ast.newSimpleName("Object"));
    }

    private List<Statement> getDependentStatements(Block block, String varName) {
        // Collect statements that use the variable
        List<Statement> dependentStatements = new ArrayList<>();
        boolean variableDeclared = false;
        for (Object obj : block.statements()) {
            Statement stmt = (Statement) obj;
            if (!variableDeclared) {
                // Skip until after the variable declaration
                if (isVariableDeclaration(stmt, varName)) {
                    variableDeclared = true;
                }
                continue;
            }
            if (usesVariable(stmt, varName)) {
                dependentStatements.add(stmt);
            }
        }
        return dependentStatements;
    }

    private boolean isVariableDeclaration(Statement stmt, String varName) {
        if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
            for (Object fragObj : varDecl.fragments()) {
                VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
                if (frag.getName().getIdentifier().equals(varName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean usesVariable(ASTNode node, String varName) {
        UsesVariableVisitor visitor = new UsesVariableVisitor(varName);
        node.accept(visitor);
        return visitor.isFound();
    }

    // Helper class to check if a node uses a variable
    private class UsesVariableVisitor extends ASTVisitor {
        private String varName;
        private boolean found = false;

        public UsesVariableVisitor(String varName) {
            this.varName = varName;
        }

        @Override
        public boolean visit(SimpleName node) {
            if (node.getIdentifier().equals(varName)) {
                found = true;
                return false; // No need to visit further
            }
            return true;
        }

        public boolean isFound() {
            return found;
        }
    }

    // Helper class to replace occurrences of the expression with the new variable
    private class ReplaceExpressionVisitor extends ASTVisitor {
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
            if (node.subtreeMatch(new ASTMatcher(), targetExpr)) {
                rewriter.replace(node, node.getAST().newSimpleName(varName), null);
            } else if (node.getExpression() != null && node.getExpression().subtreeMatch(new ASTMatcher(), targetExpr)) {
                rewriter.replace(node.getExpression(), node.getAST().newSimpleName(varName), null);
            }
        }

        @Override
        public void endVisit(FieldAccess node) {
            if (node.subtreeMatch(new ASTMatcher(), targetExpr)) {
                rewriter.replace(node, node.getAST().newSimpleName(varName), null);
            } else if (node.getExpression().subtreeMatch(new ASTMatcher(), targetExpr)) {
                rewriter.replace(node.getExpression(), node.getAST().newSimpleName(varName), null);
            }
        }
    }
}


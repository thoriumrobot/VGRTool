import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import java.util.List;

class SimplifyNullCheckRefactoring extends Refactoring {

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) node;
            Expression condition = ifStmt.getExpression();

            // Check if condition is a boolean variable
            if (condition instanceof SimpleName) {
                // Further checks can be added here
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        IfStatement ifStmt = (IfStatement) node;
        AST ast = node.getAST();

        // Get the condition variable name
        SimpleName conditionName = (SimpleName) ifStmt.getExpression();
        String varName = conditionName.getIdentifier();

        // Find the variable declaration
        VariableDeclarationFragment varDecl = findVariableDeclaration(ifStmt, varName);
        if (varDecl == null) {
            return;
        }

        // Get the original expression (e.g., x == null)
        Expression originalCondition = varDecl.getInitializer();

        // Replace the condition in the if statement
        rewriter.replace(conditionName, ASTNode.copySubtree(ast, originalCondition), null);

        // Remove the variable declaration
        rewriter.remove(varDecl.getParent(), null);
    }

// Improved findVariableDeclaration method
private VariableDeclarationFragment findVariableDeclaration(ASTNode node, String varName) {
    ASTNode current = node;
    while (current != null) {
        if (current instanceof Block) {
            Block block = (Block) current;
            for (Statement stmt : (List<Statement>) block.statements()) {
                if (stmt instanceof VariableDeclarationStatement) {
                    VariableDeclarationStatement varStmt = (VariableDeclarationStatement) stmt;
                    for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>) varStmt.fragments()) {
                        if (frag.getName().getIdentifier().equals(varName)) {
                            return frag;
                        }
                    }
                }
            }
        }
        current = current.getParent();
    }
    return null;
}

}

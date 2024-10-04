import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

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

    // Utility method to find variable declaration of a given name
    private VariableDeclarationFragment findVariableDeclaration(ASTNode node, String varName) {
        // Traverse the parent nodes to find the declaration
        ASTNode parent = node.getParent();
        while (parent != null) {
            if (parent instanceof Block) {
                Block block = (Block) parent;
                List<?> statements = block.statements();
                for (Object stmtObj : statements) {
                    if (stmtObj instanceof VariableDeclarationStatement) {
                        VariableDeclarationStatement varStmt = (VariableDeclarationStatement) stmtObj;
                        for (Object fragObj : varStmt.fragments()) {
                            VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
                            if (frag.getName().getIdentifier().equals(varName)) {
                                return frag;
                            }
                        }
                    }
                }
            }
            parent = parent.getParent();
        }
        return null;
    }
}

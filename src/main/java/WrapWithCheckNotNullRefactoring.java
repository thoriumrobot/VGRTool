import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Set;
import java.util.List;

class WrapWithCheckNotNullRefactoring extends Refactoring {

    private Set<Expression> expressionsPossiblyNull;

    public WrapWithCheckNotNullRefactoring(Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        // Check if the node is an expression that may be null
        if (node instanceof Expression) {
            Expression expr = (Expression) node;
            return expressionsPossiblyNull.contains(expr);
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        if (!(node instanceof Expression)) {
            return;
        }
        Expression expr = (Expression) node;
        AST ast = node.getAST();

        // Create the checkNotNull method invocation
        MethodInvocation checkNotNullInvocation = ast.newMethodInvocation();

        // Determine the appropriate utility class (e.g., Utils or Objects)
        SimpleName utilityClassName;
        if (isClassAvailable(node, "Utils")) {
            utilityClassName = ast.newSimpleName("Utils");
            checkNotNullInvocation.setName(ast.newSimpleName("checkNotNull"));
        } else {
            utilityClassName = ast.newSimpleName("Objects");
            checkNotNullInvocation.setName(ast.newSimpleName("requireNonNull"));
        }

        checkNotNullInvocation.setExpression(utilityClassName);

        // Add the original expression as an argument
        checkNotNullInvocation.arguments().add(ASTNode.copySubtree(ast, expr));

        // Optionally, add a message argument if using Utils.checkNotNull
        if (utilityClassName.getIdentifier().equals("Utils")) {
            StringLiteral message = ast.newStringLiteral();
            message.setLiteralValue(expr.toString() + " is null");
            checkNotNullInvocation.arguments().add(message);
        }

        // Replace the original expression with the wrapped one
        rewriter.replace(expr, checkNotNullInvocation, null);
    }

    // Helper method to check if a class is available in the context
    private boolean isClassAvailable(ASTNode node, String className) {
        CompilationUnit cu = (CompilationUnit) node.getRoot();
        for (ImportDeclaration importDecl : (List<ImportDeclaration>) cu.imports()) {
            if (importDecl.getName().getFullyQualifiedName().endsWith(className)) {
                return true;
            }
        }
        // Also check if the class is in the same package
        if (cu.getPackage() != null && cu.getPackage().getName().getFullyQualifiedName().endsWith(className)) {
            return true;
        }
        return className.equals("Objects"); // java.util.Objects is always available
    }
}


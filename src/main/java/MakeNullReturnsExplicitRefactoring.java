import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import java.util.*;

class MakeNullReturnsExplicitRefactoring extends Refactoring {

    @Override
    public boolean isApplicable(ASTNode node) {
        // Apply this refactoring to method declarations
        return node instanceof MethodDeclaration;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        if (!(node instanceof MethodDeclaration)) {
            return;
        }

        MethodDeclaration methodDecl = (MethodDeclaration) node;
        Block body = methodDecl.getBody();

        if (body == null) {
            return;
        }

        // Check if method can return null without being annotated as @Nullable
        if (!returnsNullable(methodDecl)) {
            return;
        }

        // Modify return statements to make null returns explicit
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(ReturnStatement returnStmt) {
                Expression expr = returnStmt.getExpression();
                if (expr == null) {
                    // Return without expression (void method), skip
                    return false;
                }

                ITypeBinding returnType = methodDecl.getReturnType2().resolveBinding();
                if (returnType == null) {
                    return false;
                }

                if (isNullLiteral(expr)) {
                    // Return statement already returns null literal, no change needed
                    return false;
                }

                // Check if expression can be null
                if (canExpressionBeNull(expr)) {
                    // Wrap the expression with a checkNotNull call
                    AST ast = node.getAST();
                    MethodInvocation checkNotNullInvocation = createCheckNotNullInvocation(ast, expr);
                    rewriter.replace(expr, checkNotNullInvocation, null);
                }

                return false;
            }
        });
    }

    private boolean returnsNullable(MethodDeclaration methodDecl) {
        // Check if method has any return statements that return null
        ReturnNullVisitor visitor = new ReturnNullVisitor();
        methodDecl.accept(visitor);
        return visitor.hasNullReturn;
    }

    private boolean isNullLiteral(Expression expr) {
        return expr instanceof NullLiteral;
    }

    private boolean canExpressionBeNull(Expression expr) {
        // Simple heuristic: if the expression is a variable or method call, assume it can be null
        return expr instanceof SimpleName || expr instanceof MethodInvocation || expr instanceof FieldAccess;
    }

    private MethodInvocation createCheckNotNullInvocation(AST ast, Expression expr) {
        // Create a call to checkNotNull(expression, "message")
        MethodInvocation checkNotNullInvocation = ast.newMethodInvocation();
        checkNotNullInvocation.setExpression(ast.newSimpleName("Utils"));
        checkNotNullInvocation.setName(ast.newSimpleName("checkNotNull"));
        checkNotNullInvocation.arguments().add(ASTNode.copySubtree(ast, expr));
        StringLiteral message = ast.newStringLiteral();
        message.setLiteralValue(expr.toString() + " == null");
        checkNotNullInvocation.arguments().add(message);
        return checkNotNullInvocation;
    }

    // Helper visitor to determine if method returns null
    private static class ReturnNullVisitor extends ASTVisitor {
        boolean hasNullReturn = false;

        @Override
        public boolean visit(ReturnStatement returnStmt) {
            Expression expr = returnStmt.getExpression();
            if (expr instanceof NullLiteral) {
                hasNullReturn = true;
            }
            return false;
        }
    }
}


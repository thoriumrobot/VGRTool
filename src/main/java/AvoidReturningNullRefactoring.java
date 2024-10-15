import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import java.util.*;

class AvoidReturningNullRefactoring extends Refactoring {

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

        // Determine the return type
        Type returnType = methodDecl.getReturnType2();
        ITypeBinding returnTypeBinding = returnType.resolveBinding();
        if (returnTypeBinding == null) {
            return;
        }

        // Modify return statements to prevent returning null
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(ReturnStatement returnStmt) {
                Expression expr = returnStmt.getExpression();
                if (expr == null) {
                    // Return without expression (void method), skip
                    return false;
                }

                if (isNullLiteral(expr)) {
                    // Replace 'return null;' with an appropriate default value or throw exception
                    AST ast = node.getAST();
                    Statement newStatement = createReplacementStatement(ast, returnTypeBinding, returnStmt);
                    if (newStatement != null) {
                        rewriter.replace(returnStmt, newStatement, null);
                    }
                } else if (canExpressionBeNull(expr)) {
                    // Wrap the expression with Objects.requireNonNull
                    AST ast = node.getAST();
                    MethodInvocation requireNonNullInvocation = createRequireNonNullInvocation(ast, expr);
                    rewriter.replace(expr, requireNonNullInvocation, null);
                }

                return false;
            }
        });
    }

    private boolean returnsNullable(MethodDeclaration methodDecl) {
        // Check if method has any return statements that return null
        ReturnNullVisitor visitor = new ReturnNullVisitor();
        methodDecl.accept(visitor);
        return visitor.hasNullReturn || visitor.hasPotentialNullReturn;
    }

    private boolean isNullLiteral(Expression expr) {
        return expr instanceof NullLiteral;
    }

    private boolean canExpressionBeNull(Expression expr) {
        // Simple heuristic: if the expression is a variable or method call, assume it can be null
        return expr instanceof SimpleName || expr instanceof MethodInvocation || expr instanceof FieldAccess;
    }

    private MethodInvocation createRequireNonNullInvocation(AST ast, Expression expr) {
        // Create a call to Objects.requireNonNull(expression, "message")
        MethodInvocation requireNonNullInvocation = ast.newMethodInvocation();
        requireNonNullInvocation.setExpression(ast.newSimpleName("Objects"));
        requireNonNullInvocation.setName(ast.newSimpleName("requireNonNull"));
        requireNonNullInvocation.arguments().add(ASTNode.copySubtree(ast, expr));
        StringLiteral message = ast.newStringLiteral();
        message.setLiteralValue(expr.toString() + " is null");
        requireNonNullInvocation.arguments().add(message);
        return requireNonNullInvocation;
    }

    private Statement createReplacementStatement(AST ast, ITypeBinding returnTypeBinding, ReturnStatement originalReturnStmt) {
        if (returnTypeBinding.isPrimitive()) {
            // For primitive types, return the default value (e.g., 0 for int)
            Expression defaultValue = getDefaultValueForPrimitiveType(ast, returnTypeBinding);
            if (defaultValue != null) {
                ReturnStatement newReturnStmt = ast.newReturnStatement();
                newReturnStmt.setExpression(defaultValue);
                return newReturnStmt;
            }
        } else if (returnTypeBinding.getQualifiedName().equals("java.lang.String")) {
            // For String, return an empty string
            ReturnStatement newReturnStmt = ast.newReturnStatement();
            StringLiteral emptyString = ast.newStringLiteral();
            emptyString.setLiteralValue("");
            newReturnStmt.setExpression(emptyString);
            return newReturnStmt;
        } else if (returnTypeBinding.isAssignmentCompatible(ast.resolveWellKnownType("java.util.List"))) {
            // For List types, return Collections.emptyList()
            ReturnStatement newReturnStmt = ast.newReturnStatement();
            MethodInvocation emptyListInvocation = ast.newMethodInvocation();
            emptyListInvocation.setExpression(ast.newName("Collections"));
            emptyListInvocation.setName(ast.newSimpleName("emptyList"));
            newReturnStmt.setExpression(emptyListInvocation);
            return newReturnStmt;
        } else {
            // For other reference types, throw an exception
            ThrowStatement throwStmt = ast.newThrowStatement();
            ClassInstanceCreation exceptionCreation = ast.newClassInstanceCreation();
            exceptionCreation.setType(ast.newSimpleType(ast.newSimpleName("IllegalStateException")));
            StringLiteral message = ast.newStringLiteral();
            message.setLiteralValue("Method returned null");
            exceptionCreation.arguments().add(message);
            throwStmt.setExpression(exceptionCreation);
            return throwStmt;
        }
        return null;
    }

    private Expression getDefaultValueForPrimitiveType(AST ast, ITypeBinding typeBinding) {
        if (typeBinding.getName().equals("boolean")) {
            return ast.newBooleanLiteral(false);
        } else if (typeBinding.getName().equals("char")) {
            CharacterLiteral charLiteral = ast.newCharacterLiteral();
            charLiteral.setCharValue('\u0000');
            return charLiteral;
        } else if (typeBinding.getName().equals("byte") ||
                   typeBinding.getName().equals("short") ||
                   typeBinding.getName().equals("int") ||
                   typeBinding.getName().equals("long") ||
                   typeBinding.getName().equals("float") ||
                   typeBinding.getName().equals("double")) {
            return ast.newNumberLiteral("0");
        }
        return null;
    }

    // Helper visitor to determine if method returns null
    private static class ReturnNullVisitor extends ASTVisitor {
        boolean hasNullReturn = false;
        boolean hasPotentialNullReturn = false;

        @Override
        public boolean visit(ReturnStatement returnStmt) {
            Expression expr = returnStmt.getExpression();
            if (expr instanceof NullLiteral) {
                hasNullReturn = true;
            } else if (expr != null && couldBeNull(expr)) {
                hasPotentialNullReturn = true;
            }
            return false;
        }

        private boolean couldBeNull(Expression expr) {
            // Heuristic to determine if the expression could be null
            return expr instanceof SimpleName || expr instanceof MethodInvocation || expr instanceof FieldAccess;
        }
    }
}


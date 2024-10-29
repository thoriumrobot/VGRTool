import java.util.HashSet;
import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.example.utils.RefactoringUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CharacterLiteral;

/**
 * Refactoring to add nullability checks to fields 
 * and ensure safe access throughout the code.
 */
public class NullabilityRefactoring extends Refactoring {

    private Set<String> possiblyNullFields = new HashSet<>();

    @Override
    public boolean isApplicable(ASTNode node) {
        return node instanceof CompilationUnit || node instanceof MethodDeclaration;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        if (node instanceof CompilationUnit) {
            handleCompilationUnit((CompilationUnit) node, rewriter);
        } else if (node instanceof MethodDeclaration) {
            handleMethodDeclaration((MethodDeclaration) node, rewriter);
        }
    }

    private void handleCompilationUnit(CompilationUnit cu, ASTRewrite rewriter) {
        // Collect possibly null fields
        collectPossiblyNullFields(cu);

        // Add null checks for field accesses
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration methodDeclaration) {
                Block body = methodDeclaration.getBody();
                if (body != null) {
                    body.accept(new ASTVisitor() {
                        @Override
                        public void preVisit(ASTNode node) {
                            if (node instanceof FieldAccess || node instanceof SimpleName) {
                                handleFieldAccess(node, rewriter);
                            }
                        }
                    });
                }
                return false;
            }
        });
    }

    private void collectPossiblyNullFields(CompilationUnit cu) {
        // Collect fields that are declared but not initialized and are not final
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration typeDeclaration) {
                for (FieldDeclaration field : typeDeclaration.getFields()) {
                    boolean isFinal = Modifier.isFinal(field.getModifiers());

                    for (Object fragmentObj : field.fragments()) {
                        VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObj;
                        String fieldName = fragment.getName().getIdentifier();

                        if (fragment.getInitializer() == null && !isFinal) {
                            possiblyNullFields.add(fieldName);
                        }
                    }
                }
                return false;
            }
        });
    }

    private void handleFieldAccess(ASTNode node, ASTRewrite rewriter) {
        String fieldName = RefactoringUtils.getFieldName(node);

        if (fieldName != null && possiblyNullFields.contains(fieldName)) {
            // Check if the field access is already guarded by a null check
            if (!isGuardedByNullCheck(node)) {
                insertNullCheck(node, rewriter);
            }
        }
    }

    private boolean isGuardedByNullCheck(ASTNode node) {
        // Traverse up the AST to check if the field access is within a null check
        ASTNode parent = node.getParent();
        while (parent != null) {
            if (parent instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) parent;
                Expression condition = ifStmt.getExpression();

                if (isNullCheck(condition, RefactoringUtils.getFieldName(node))) {
                    return true;
                }
            } else if (parent instanceof MethodDeclaration || parent instanceof TypeDeclaration) {
                break;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isNullCheck(Expression expr, String fieldName) {
        // Check if the expression is a null check of the form 'fieldName != null' or 'fieldName == null'
        if (expr instanceof InfixExpression) {
            InfixExpression infixExpr = (InfixExpression) expr;
            if (infixExpr.getOperator() == InfixExpression.Operator.NOT_EQUALS ||
                infixExpr.getOperator() == InfixExpression.Operator.EQUALS) {

                Expression left = infixExpr.getLeftOperand();
                Expression right = infixExpr.getRightOperand();

                if ((isFieldReference(left, fieldName) && right instanceof NullLiteral) ||
                    (left instanceof NullLiteral && isFieldReference(right, fieldName))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFieldReference(Expression expr, String fieldName) {
        if (expr instanceof SimpleName) {
            SimpleName simpleName = (SimpleName) expr;
            return simpleName.getIdentifier().equals(fieldName) && RefactoringUtils.isField(simpleName);
        } else if (expr instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expr;
            return fieldAccess.getName().getIdentifier().equals(fieldName);
        }
        return false;
    }

    private void insertNullCheck(ASTNode node, ASTRewrite rewriter) {
        AST ast = node.getAST();

        // Find the containing statement
        Statement containingStatement = getContainingStatement(node);
        if (containingStatement == null) {
            return;
        }

        // Create a new 'if' statement
        IfStatement ifStatement = ast.newIfStatement();

        // Build the condition 'fieldName != null'
        InfixExpression condition = ast.newInfixExpression();
        condition.setOperator(InfixExpression.Operator.NOT_EQUALS);
        condition.setLeftOperand(createFieldExpression(node, ast));
        condition.setRightOperand(ast.newNullLiteral());

        ifStatement.setExpression(condition);

        // Move the original statement into the 'then' block
        ifStatement.setThenStatement((Statement) rewriter.createCopyTarget(containingStatement));

        // Replace the original statement with the new 'if' statement
        rewriter.replace(containingStatement, ifStatement, null);
    }

    private Expression createFieldExpression(ASTNode node, AST ast) {
        if (node instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) node;
            FieldAccess newFieldAccess = ast.newFieldAccess();
            newFieldAccess.setExpression((Expression) ASTNode.copySubtree(ast, fieldAccess.getExpression()));
            newFieldAccess.setName(ast.newSimpleName(fieldAccess.getName().getIdentifier()));
            return newFieldAccess;
        } else if (node instanceof SimpleName) {
            return ast.newSimpleName(((SimpleName) node).getIdentifier());
        }
        return null;
    }

    private Statement getContainingStatement(ASTNode node) {
        ASTNode parent = node.getParent();
        while (parent != null && !(parent instanceof Statement)) {
            parent = parent.getParent();
        }
        return (Statement) parent;
    }

    private void handleMethodDeclaration(MethodDeclaration methodDecl, ASTRewrite rewriter) {
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

        Block body = methodDecl.getBody();
        if (body == null) {
            return;
        }

        // Modify return statements
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(ReturnStatement returnStmt) {
                Expression expr = returnStmt.getExpression();
                if (expr == null) {
                    // Return without expression (void method), skip
                    return false;
                }

                if (isNullLiteral(expr)) {
                    // Replace 'return null;' with default value or throw exception
                    AST ast = methodDecl.getAST();
                    Statement newStatement = createReplacementStatement(ast, returnTypeBinding, returnStmt);
                    if (newStatement != null) {
                        rewriter.replace(returnStmt, newStatement, null);
                    }
                } else if (canExpressionBeNull(expr)) {
                    // Wrap the expression with Objects.requireNonNull
                    AST ast = methodDecl.getAST();
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
        } else if (implementsInterface(returnTypeBinding, "java.util.Collection")) {
            // For Collection types, return Collections.emptyList()
            ReturnStatement newReturnStmt = ast.newReturnStatement();
            MethodInvocation emptyCollectionInvocation = ast.newMethodInvocation();
            emptyCollectionInvocation.setExpression(ast.newName("Collections"));
            emptyCollectionInvocation.setName(ast.newSimpleName("emptyList"));
            newReturnStmt.setExpression(emptyCollectionInvocation);
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

    private boolean implementsInterface(ITypeBinding typeBinding, String interfaceName) {
        if (typeBinding == null) {
            return false;
        }
        if (typeBinding.getQualifiedName().equals(interfaceName)) {
            return true;
        }
        for (ITypeBinding iface : typeBinding.getInterfaces()) {
            if (implementsInterface(iface, interfaceName)) {
                return true;
            }
        }
        ITypeBinding superclass = typeBinding.getSuperclass();
        if (superclass != null) {
            return implementsInterface(superclass, interfaceName);
        }
        return false;
    }

    private Expression getDefaultValueForPrimitiveType(AST ast, ITypeBinding typeBinding) {
        switch (typeBinding.getName()) {
            case "boolean":
                return ast.newBooleanLiteral(false);
            case "char":
                CharacterLiteral charLiteral = ast.newCharacterLiteral();
                charLiteral.setCharValue('\u0000');
                return charLiteral;
            case "byte":
            case "short":
            case "int":
            case "long":
            case "float":
            case "double":
                return ast.newNumberLiteral("0");
            default:
                return null;
        }
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


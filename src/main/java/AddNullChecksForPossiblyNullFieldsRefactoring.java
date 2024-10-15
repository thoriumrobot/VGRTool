import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import java.util.*;

class AddNullChecksForPossiblyNullFieldsRefactoring extends Refactoring {

    private Set<String> possiblyNullFields = new HashSet<>();

    @Override
    public boolean isApplicable(ASTNode node) {
        // Apply this refactoring to the entire compilation unit
        return node instanceof CompilationUnit;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        if (!(node instanceof CompilationUnit)) {
            return;
        }
        CompilationUnit cu = (CompilationUnit) node;

        // Collect fields that are possibly null
        collectPossiblyNullFields(cu);

        // Visit method bodies and insert null checks where necessary
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
        String fieldName = getFieldName(node);

        if (fieldName != null && possiblyNullFields.contains(fieldName)) {
            // Check if the field access is already guarded by a null check
            if (!isGuardedByNullCheck(node)) {
                insertNullCheck(node, rewriter);
            }
        }
    }

    private String getFieldName(ASTNode node) {
        if (node instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) node;
            return fieldAccess.getName().getIdentifier();
        } else if (node instanceof SimpleName) {
            SimpleName simpleName = (SimpleName) node;

            // Ensure it's a field access, not a local variable or method name
            if (!simpleName.isDeclaration() && isField(simpleName)) {
                return simpleName.getIdentifier();
            }
        }
        return null;
    }

    private boolean isField(SimpleName simpleName) {
        IBinding binding = simpleName.resolveBinding();
        return binding instanceof IVariableBinding && ((IVariableBinding) binding).isField();
    }

    private boolean isGuardedByNullCheck(ASTNode node) {
        // Traverse up the AST to check if the field access is within a null check
        ASTNode parent = node.getParent();
        while (parent != null) {
            if (parent instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) parent;
                Expression condition = ifStmt.getExpression();

                if (isNullCheck(condition, getFieldName(node))) {
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
            return simpleName.getIdentifier().equals(fieldName) && isField(simpleName);
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
}


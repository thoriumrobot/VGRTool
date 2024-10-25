import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Set;
import java.util.List;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

class AddNullCheckBeforeMethodCallRefactoring extends Refactoring {

    private Set<IVariableBinding> variablesPossiblyNull;
    private Set<IMethodBinding> methodsExpectingNonNull;

    public AddNullCheckBeforeMethodCallRefactoring(
            Set<IVariableBinding> variablesPossiblyNull,
            Set<IMethodBinding> methodsExpectingNonNull) {
        this.variablesPossiblyNull = variablesPossiblyNull;
        this.methodsExpectingNonNull = methodsExpectingNonNull;
    }

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof MethodInvocation || node instanceof ClassInstanceCreation) {
            ASTNode invocationNode = node;
            IMethodBinding methodBinding = null;
            if (invocationNode instanceof MethodInvocation) {
                methodBinding = ((MethodInvocation) invocationNode).resolveMethodBinding();
            } else if (invocationNode instanceof ClassInstanceCreation) {
                methodBinding = ((ClassInstanceCreation) invocationNode).resolveConstructorBinding();
            }

            if (methodBinding != null && methodsExpectingNonNull.contains(methodBinding.getMethodDeclaration())) {
                List<Expression> arguments = getArguments(invocationNode);
                for (Expression arg : arguments) {
                    IVariableBinding varBinding = getVariableBinding(arg);
                    if (varBinding != null && variablesPossiblyNull.contains(varBinding.getVariableDeclaration())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        ASTNode invocationNode = node;
        AST ast = node.getAST();

        List<Expression> arguments = getArguments(invocationNode);
        Block parentBlock = getParentBlock(node);
        if (parentBlock == null) {
            return;
        }

        for (int i = 0; i < arguments.size(); i++) {
            Expression arg = arguments.get(i);
            IVariableBinding varBinding = getVariableBinding(arg);
            if (varBinding != null && variablesPossiblyNull.contains(varBinding.getVariableDeclaration())) {
                // Insert null check before the method invocation
                IfStatement ifStatement = ast.newIfStatement();

                InfixExpression condition = ast.newInfixExpression();
                condition.setLeftOperand((Expression) ASTNode.copySubtree(ast, arg));
                condition.setOperator(InfixExpression.Operator.EQUALS);
                condition.setRightOperand(ast.newNullLiteral());

                ThrowStatement throwStatement = ast.newThrowStatement();
                ClassInstanceCreation exceptionCreation = ast.newClassInstanceCreation();
                exceptionCreation.setType(ast.newSimpleType(ast.newSimpleName("IllegalArgumentException")));
                StringLiteral message = ast.newStringLiteral();
                message.setLiteralValue(varBinding.getName() + " should not be null");
                exceptionCreation.arguments().add(message);
                throwStatement.setExpression(exceptionCreation);

                ifStatement.setExpression(condition);
                ifStatement.setThenStatement(throwStatement);

                // Insert the if statement before the method invocation
                ListRewrite listRewrite = rewriter.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
                listRewrite.insertBefore(ifStatement, getEnclosingStatement(node), null);

                // Update the variable in the set to prevent multiple checks
                variablesPossiblyNull.remove(varBinding.getVariableDeclaration());
            }
        }
    }

    private IVariableBinding getVariableBinding(Expression expr) {
        if (expr instanceof SimpleName) {
            SimpleName simpleName = (SimpleName) expr;
            IBinding binding = simpleName.resolveBinding();
            if (binding instanceof IVariableBinding) {
                return (IVariableBinding) binding;
            }
        } else if (expr instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expr;
            IBinding binding = fieldAccess.resolveFieldBinding();
            if (binding instanceof IVariableBinding) {
                return (IVariableBinding) binding;
            }
        } else if (expr instanceof MethodInvocation) {
            // Handle method invocations if necessary
        }
        return null;
    }

    private Block getParentBlock(ASTNode node) {
        ASTNode parent = node.getParent();
        while (parent != null && !(parent instanceof Block)) {
            parent = parent.getParent();
        }
        return (Block) parent;
    }

    private Statement getEnclosingStatement(ASTNode node) {
        ASTNode parent = node;
        while (parent != null && !(parent instanceof Statement)) {
            parent = parent.getParent();
        }
        return (Statement) parent;
    }

    private List<Expression> getArguments(ASTNode invocationNode) {
        if (invocationNode instanceof MethodInvocation) {
            return ((MethodInvocation) invocationNode).arguments();
        } else if (invocationNode instanceof ClassInstanceCreation) {
            return ((ClassInstanceCreation) invocationNode).arguments();
        }
        return null;
    }
}


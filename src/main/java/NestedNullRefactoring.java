
import java.lang.reflect.Modifier;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * This class represents a refactoring in which invocations to private one line
 * methods which return the nullness of a value are replaced with the method's
 * one-line null check. Preserves semantics by copying the invoked code
 * precisely.
 */
public class NestedNullRefactoring extends Refactoring {
	public static final String NAME = "NestedNullRefactoring";

	private final Dictionary<IMethodBinding, Expression> applicableMethods;

	public NestedNullRefactoring() {
		applicableMethods = new Hashtable<>();
	}

	@Override
	public boolean isApplicable(ASTNode node) {

		if (node instanceof MethodInvocation invocation) {
			return isApplicable(invocation);
		}

		if (node instanceof MethodDeclaration declaration) {
			return isApplicable(declaration);
		}

		return false;

	}

	/*
	 * Returns true iff the provided invocation is of a registered one-line method
	 * that returns the result of a null check
	 */
	public boolean isApplicable(MethodInvocation invocation) {
		// Check if Method Invocation is in applicableMethods
		if (applicableMethods.get(invocation.resolveMethodBinding()) != null) {
			System.out.println("[DEBUG] Invocation of applicable method found");
			return true;
		}
		return false;
	}

	/*
	 * Returns true iff Node is a one-line method that returns the result of a null
	 * check
	 */
	public boolean isApplicable(MethodDeclaration declaration) {
		// Confirm that the method returns a boolean
		Type retType = declaration.getReturnType2();
		boolean isBooleanDeclaration = (retType.isPrimitiveType()
				&& ((PrimitiveType) retType).getPrimitiveTypeCode() == PrimitiveType.BOOLEAN);
		if (!(isBooleanDeclaration)) {
			return false;
		}

		/*
		 * Ensure the method declaration is private or package-private (default if not
		 * modifier).
		 * https://docs.oracle.com/javase/tutorial/java/javaOO/accesscontrol.html
		 */
		if (((declaration.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC)
				|| (declaration.getModifiers() & Modifier.PROTECTED) == Modifier.PROTECTED) {
			return false;
		}

		// Checks if there are any parameters
		// TODO: Make work with Parameters
		boolean hasParams = declaration.parameters().size() > 0;
		if (hasParams) {
			return false;
		}

		Block body = declaration.getBody();
		List<Statement> stmts = body.statements();

		// Checks if there is only one line
		boolean isOneLine = stmts.size() == 1;
		if (!isOneLine) {
			return false;
		}

		// Possible unneccessary since we already confirmed return type is not void
		Statement stmt = stmts.get(0);
		if (!(stmt instanceof ReturnStatement)) {
			return false;
		}

		// Checks to make sure return statement is of a single equality check
		Expression retExpr = ((ReturnStatement) stmt).getExpression();
		if (!(retExpr instanceof InfixExpression)) {
			return false;
		}

		InfixExpression infix = (InfixExpression) retExpr;

		if ((infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
				|| infix.getOperator() == InfixExpression.Operator.EQUALS)) {
			Expression leftOperand = infix.getLeftOperand();
			Expression rightOperand = infix.getRightOperand();

			if ((leftOperand instanceof SimpleName && rightOperand instanceof NullLiteral)
					|| (rightOperand instanceof SimpleName && leftOperand instanceof NullLiteral)) {
				System.out.println("[DEBUG] Found one line null check method: " + declaration.getName());
				applicableMethods.put((declaration.resolveBinding()), retExpr);
			}
		}
		return false;
	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		// Check if Method Invocation is in applicableMethods
		if (node instanceof MethodInvocation invocation) {
			replace(node, rewriter, invocation);

		} else if (node instanceof PrefixExpression prefix && prefix.getOperator() == PrefixExpression.Operator.NOT
				&& prefix.getOperand() instanceof MethodInvocation invocation) {
			replace(node, rewriter, invocation);
		}
	}

	private void replace(ASTNode node, ASTRewrite rewriter, MethodInvocation invocation) {
		Expression expr = (applicableMethods.get((invocation.resolveMethodBinding())));
		if (expr == null) {
			System.err.println("Cannot find applicable method for refactoring. ");
			return;
		}

		AST ast = node.getAST();

		ParenthesizedExpression pExpr = ast.newParenthesizedExpression();
		Expression copiedExpression = (Expression) ASTNode.copySubtree(ast, expr);
		System.out.println("Expression: " + expr + "\nCopied expression: " + copiedExpression);
		pExpr.setExpression(copiedExpression);

		System.out.println("Refactoring " + node + "\n\tReplacing \n\t" + invocation + "\n\tWith \n\t" + pExpr);
		rewriter.replace(invocation, pExpr, null);

	}

}

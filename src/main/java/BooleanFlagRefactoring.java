import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * This class represents a refactoring in which boolean flags are replaced with
 * explicit null checks
 */
public class BooleanFlagRefactoring extends Refactoring {
	public static final String NAME = "BooleanFlagRefactoring";

	/**
	 * List of variable names identified as boolean flags, along with their
	 * corresponding initializer expression
	 */
	private final Map<String, Expression> flagExpressions;

	/** Default constructor (for RefactoringEngine integration) */
	public BooleanFlagRefactoring() {
		super();
		this.flagExpressions = new HashMap<>();
	}

	@Override
	public boolean isApplicable(ASTNode node) {
		if (node instanceof VariableDeclarationStatement stmt) {
			return isApplicable(stmt);
		} else if (node instanceof IfStatement ifStmt) {
			return isApplicable(ifStmt);
		} else if (node instanceof Assignment assignment) {
			checkReassignment(assignment);
		}
		return false;
	}

	/**
	 * Checks to see if a VariableDeclarationStatement defines a boolean flag that
	 * represents another variable's nullness
	 */
	private boolean isApplicable(VariableDeclarationStatement stmt) {
		boolean isBooleanDeclaration = (stmt.getType() instanceof PrimitiveType pType
				&& pType.getPrimitiveTypeCode() == PrimitiveType.BOOLEAN);

		if (!isBooleanDeclaration) {
			return false;
		}

		boolean flagFound = false;

		// Search through all declared variables in declaration node for a booleanflag
		List<VariableDeclarationFragment> fragments = stmt.fragments();
		for (VariableDeclarationFragment frag : fragments) {
			SimpleName varName = frag.getName();
			Expression varInitializer = frag.getInitializer();
			List<Expression> initExpr = Refactoring.getSubExpressions(varInitializer);
			for (Expression expression : initExpr) {
				if (expression instanceof ConditionalExpression cExpr) {
					expression = cExpr.getExpression();
				}
				if (expression instanceof InfixExpression infix && isEqualityOperator(infix.getOperator())
						&& getNullComparisonVariable(infix) != null) {
					AST ast = stmt.getAST();
					ParenthesizedExpression pExpr = ast.newParenthesizedExpression();
					Expression copiedExpression = (Expression) ASTNode.copySubtree(ast, varInitializer);
					pExpr.setExpression(copiedExpression);
					flagExpressions.put(varName.getIdentifier(), pExpr);
					flagFound = true;
				}
			}
		}
		return flagFound;
	}

	/**
	 * Analyzes an IfStatement to see if it contains a check utilizing an identified
	 * boolean flag
	 */
	private boolean isApplicable(IfStatement ifStmt) {
		List<Expression> exprFragments = Refactoring.getSubExpressions(ifStmt.getExpression());
		for (Expression expr : exprFragments) {
			if (expr instanceof InfixExpression infix && isEqualityOperator(infix.getOperator())) {
				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();

				if ((leftOperand instanceof SimpleName lhs && isFlag(lhs))
						|| (rightOperand instanceof SimpleName rhs && isFlag(rhs))) {
					return true;
				}
			}
			if (expr instanceof SimpleName varName && isFlag(varName)) {
				return true;
			}
		}
		return false;
	}

	private boolean isFlag(SimpleName potentialFlag) {
		return flagExpressions.get(potentialFlag.getIdentifier()) != null;
	}

	private boolean isEqualityOperator(Operator op) {
		return (op == Operator.NOT_EQUALS || op == Operator.EQUALS);
	}

	private SimpleName getNullComparisonVariable(InfixExpression infix) {
		Expression leftOperand = infix.getLeftOperand();
		Expression rightOperand = infix.getRightOperand();
		if (leftOperand instanceof SimpleName varName && rightOperand instanceof NullLiteral) {
			return varName;
		} else if (rightOperand instanceof SimpleName varName && leftOperand instanceof NullLiteral) {
			return varName;
		}
		return null;

	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		if (!(node instanceof IfStatement ifStmt)) {
			return;
		}
		List<Expression> exprFragments = Refactoring.getSubExpressions(ifStmt.getExpression());
		for (Expression expression : exprFragments) {
			if (expression instanceof InfixExpression infix && isEqualityOperator(infix.getOperator())) {
				SimpleName varName = getNullComparisonVariable(infix);
				Expression newExpr = flagExpressions.get(varName.getIdentifier());
				if (newExpr != null) {
					rewriter.replace(varName, newExpr, null);
				}
			}
			if (expression instanceof SimpleName sn) {
				Expression newExpr = flagExpressions.get(sn.getIdentifier());
				if (newExpr != null) {
					rewriter.replace(sn, newExpr, null);
				}
			}
		}
	}

	/*
	 * Checks Assignment node to see if it re-assigns an existing boolean flag, and
	 * if so removes the flag from flagExpressions
	 */
	private void checkReassignment(Assignment assignmentNode) {
		Expression lhs = assignmentNode.getLeftHandSide();
		if (!(lhs instanceof SimpleName varName)) {
			return;
		}
		if (isFlag(varName)) {
			flagExpressions.remove(varName.getIdentifier());
		}
	}
}

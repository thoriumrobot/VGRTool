import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * This class represents a refactoring in which integer variables whose values
 * represent the nullness of another variables are refactored into explicit null
 * checks
 */
public class SentinelRefactoring extends Refactoring {
	public static final String NAME = "SentinelRefactoring";
	/**
	 * List of all sentinels found during traversal of the AST
	 */
	private final Dictionary<String, Sentinel> sentinels;

	/**
	 * Used to skip reparsing AST elements that have already been analyzed
	 */
	private boolean skipSentinelDeclaration = false;

	/**
	 * Helper class for storing the values of a sentinel reference
	 */
	protected class Sentinel {
		private SimpleName sentinelName;
		private SimpleName VarName;
		private String nullValue;
		private String notNullValue;

		public Sentinel() {
		}

		public String getNullValue() {
			return this.nullValue;
		}

		public String getNotNullValue() {
			return this.notNullValue;
		}

		public void setNullCheck(String nullValue) {
			this.nullValue = nullValue;
		}

		public void setNotNullCheck(String notNullValue) {
			this.notNullValue = notNullValue;
		}

		public SimpleName getSentinelName() {
			return this.sentinelName;
		}

		public SimpleName setSentinelName(SimpleName sentinelName) {
			return this.sentinelName = sentinelName;
		}

		public SimpleName getVarName() {
			return this.VarName;
		}

		public SimpleName setVarName(SimpleName VarName) {
			return this.VarName = VarName;
		}

		public String toString() {
			return "Sentinel: \n\tnullValue: " + nullValue + "\n\tnotNullValue: " + notNullValue;
		}
	}

	public SentinelRefactoring() {
		super();
		this.sentinels = new Hashtable<>();
	}

	/*
	 * Checks Assignment node to see if it re-assigns an existing boolean flag, and
	 * if so removes the flag from booleanFlags
	 */
	private void checkReassignment(Assignment assignmentNode) {
		Expression lhs = assignmentNode.getLeftHandSide();
		if (!(lhs instanceof SimpleName varName)) {
			return;
		}
		if (sentinels.get(varName.toString()) != null) {
			sentinels.remove(varName.toString());
		}
	}

	/*
	 * Checks VariableDeclaration to see if a new variable is declared which shadows
	 * a global variable used as a Sentinel.
	 */
	private void checkShadowing(VariableDeclarationStatement declaration) {
		for (VariableDeclarationFragment fragment : (List<VariableDeclarationFragment>) declaration.fragments()) {
			SimpleName varName = fragment.getName();
			if (sentinels.get(varName.toString()) != null) {
				sentinels.remove(varName.toString());
			}

		}
	}

	@Override
	public boolean isApplicable(ASTNode node) {
		if (skipSentinelDeclaration) {
			if (node instanceof VariableDeclarationStatement || node instanceof Assignment) {
				skipSentinelDeclaration = false;
			}
			return false;
		}
		if (node instanceof IfStatement ifStmt) {
			return isApplicable(ifStmt);
		}
		if (node instanceof Assignment assign) {
			checkReassignment(assign);
		}
		if (node instanceof VariableDeclarationStatement declaration) {
			checkShadowing(declaration);
		}
		return false;

	}

	public boolean isApplicable(IfStatement ifStmt) {
		List<Expression> exprs = Refactoring.getSubExpressions(ifStmt.getExpression());
		for (Expression expression : exprs) {
			if (!(expression instanceof InfixExpression infix)) {
				return false;
			}

			if (isApplicable(infix)) {
				return true;
			}

			parseSentinels(infix, ifStmt);
		}
		return false;
	}

	public boolean isApplicable(InfixExpression infix) {
		Expression leftOperand = infix.getLeftOperand();
		Expression rightOperand = infix.getRightOperand();
		InfixExpression.Operator operator = infix.getOperator();

		// Check if the condition does a check on an existing sentinel
		boolean isEqualityCheck = ((operator == InfixExpression.Operator.NOT_EQUALS
				|| operator == InfixExpression.Operator.EQUALS));
		boolean usesSentinel = ((leftOperand instanceof SimpleName lhs && sentinels.get(lhs.toString()) != null)
				|| (rightOperand instanceof SimpleName rhs && sentinels.get(rhs.toString()) != null));
		if (isEqualityCheck && usesSentinel) {
			return true;
		}

		return false;
	}

	public void parseSentinels(InfixExpression infix, IfStatement ifStmt) {
		Expression leftOperand = infix.getLeftOperand();
		Expression rightOperand = infix.getRightOperand();

		// Check if condition uses a null check
		SimpleName varName = null;
		if ((leftOperand instanceof SimpleName vName && rightOperand instanceof NullLiteral)) {
			varName = vName;
		} else if ((rightOperand instanceof SimpleName vName && leftOperand instanceof NullLiteral)) {
			varName = vName;
		}

		// Get then statement
		if (!(ifStmt.getThenStatement() instanceof Block thenStmt)) {
			return;
		}

		List<Statement> stmts = thenStmt.statements();

		// Checks that there is only one line in the ifStatement
		if (stmts.size() != 1) {
			return;
		}

		// Checks that the single line is an assignment statement
		if (!(stmts.get(0) instanceof ExpressionStatement exprStmt
				&& exprStmt.getExpression() instanceof Assignment assign)) {
			return;
		}

		leftOperand = assign.getLeftHandSide();
		rightOperand = assign.getRightHandSide();
		Assignment.Operator assignOperator = assign.getOperator();
		if (assignOperator != Assignment.Operator.ASSIGN) {
			return;
		}

		SimpleName sentinelName = null;
		Expression nullValue = null;
		if (leftOperand instanceof SimpleName vName) {
			sentinelName = vName;
			nullValue = rightOperand;
		} else if (rightOperand instanceof SimpleName vName) {
			sentinelName = vName;
			nullValue = leftOperand;
		} else {
			return;
		}

		String numLiteral;
		if (nullValue instanceof NumberLiteral numLit) {
			numLiteral = numLit.getToken();
		} else if (nullValue instanceof PrefixExpression pExpr) {
			numLiteral = pExpr.toString();
		} else {
			return;
		}

		// At this point we have if (varName ?? null) { sentinel = nullValue; }
		InfixExpression.Operator operator = infix.getOperator();
		Sentinel sentinel = new Sentinel();
		sentinel.setVarName(varName);

		// if (varName != null) { sentinelName = nullValue; }
		if (operator == InfixExpression.Operator.NOT_EQUALS) {
			sentinel.setSentinelName(sentinelName);
			sentinel.setNotNullCheck(numLiteral);
		}
		// if (varName == null) { sentinelName = nullValue; }
		else if (operator == InfixExpression.Operator.EQUALS) {
			sentinel.setSentinelName(sentinelName);
			sentinel.setNullCheck(numLiteral);
		} else {
			return;
		}

		sentinels.put(sentinelName.toString(), sentinel);
		skipSentinelDeclaration = true;
	}

	public Sentinel parseElseStmt(List<Statement> stmts, Sentinel sentinel) {
		// Checks that there is only one line in the ifStatement
		if (stmts.size() != 1) {
			return null;
		}

		// Checks that the single line is an assignment statement
		if (!(stmts.get(0) instanceof ExpressionStatement exprStmt
				&& exprStmt.getExpression() instanceof Assignment assign)) {
			return null;
		}

		Expression leftOperand = assign.getLeftHandSide();
		Expression rightOperand = assign.getRightHandSide();
		Assignment.Operator assignOperator = assign.getOperator();
		if (assignOperator != Assignment.Operator.ASSIGN) {
			return null;
		}

		Expression nullValue = null;
		if (leftOperand instanceof SimpleName) {
			nullValue = rightOperand;
		} else if (rightOperand instanceof SimpleName) {
			nullValue = leftOperand;
		} else {
			return null;
		}

		String numLiteral;
		if (nullValue instanceof NumberLiteral numLit) {
			numLiteral = numLit.getToken();
		} else if (nullValue instanceof PrefixExpression pExpr) {
			numLiteral = pExpr.toString();
		} else {
			return null;
		}

		if (sentinel.getNullValue() == null) {
			sentinel.setNullCheck(numLiteral);
		} else if (sentinel.getNullValue() == null) {
			sentinel.setNotNullCheck(numLiteral);
		}

		return sentinel;
	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		if (node instanceof IfStatement ifStmt) {
			List<Expression> exprs = Refactoring.getSubExpressions(ifStmt.getExpression());
			for (Expression expression : exprs) {
				if (!(expression instanceof InfixExpression infix)) {
					return;
				}

				Expression infixLeftOperand = infix.getLeftOperand();
				Expression infixRightOperand = infix.getRightOperand();
				InfixExpression.Operator infixOperator = infix.getOperator();

				boolean isEqualityCheck = (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
						|| infix.getOperator() == InfixExpression.Operator.EQUALS);

				if (!isEqualityCheck)
					return;

				SimpleName equalityVar;
				Expression equalityExpr;

				if (infixLeftOperand instanceof SimpleName vName) {
					equalityVar = vName;
					equalityExpr = infixRightOperand;
				} else if (infixRightOperand instanceof SimpleName vName) {
					equalityVar = vName;
					equalityExpr = infixLeftOperand;
				} else {
					return;
				}

				Expression replacement = getReplacementExpression(node, equalityVar, equalityExpr, infixOperator);
				if (replacement != null) {
					rewriter.replace(expression, replacement, null);
				}

			}
		}
	}

	/**
	 * Parses an equality expression to find a check of a sentinel value
	 * 
	 * @param ast
	 *            The AST the Expression belongs to
	 * @param equalityVar
	 *            The name of the variable in the equality expression
	 * @param equalityExpr
	 *            The expression in the equality expression
	 * @param infixOperator
	 *            The operator in the equality expression
	 * @return The explicit null check the sentinel value represents, or null if not
	 *         sentinel check found
	 */
	public Expression getReplacementExpression(ASTNode node, SimpleName equalityVar, Expression equalityExpr,
			InfixExpression.Operator infixOperator) {
		Sentinel sentinel = sentinels.get(equalityVar.toString());
		if (sentinel == null)
			return null;
		AST ast = node.getAST();
		InfixExpression newCheck = ast.newInfixExpression();
		newCheck.setLeftOperand(ast.newSimpleName(sentinel.getVarName().toString()));
		newCheck.setRightOperand(ast.newNullLiteral());
		if (infixOperator == InfixExpression.Operator.EQUALS) {
			if (sentinel.getNullValue() != null) {
				if (equalityExpr.toString().equals(sentinel.getNullValue())) {
					newCheck.setOperator(InfixExpression.Operator.EQUALS);
				} else {
					newCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
				}
			} else if (sentinel.getNotNullValue() != null) {
				if (equalityExpr.toString().equals(sentinel.getNotNullValue())) {
					newCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
				} else {
					newCheck.setOperator(InfixExpression.Operator.EQUALS);
				}
			}
			return newCheck;
		} else if (infixOperator == InfixExpression.Operator.NOT_EQUALS) {
			if (sentinel.getNullValue() != null) {
				if (equalityExpr.toString().equals(sentinel.getNullValue())) {
					newCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
				} else {
					newCheck.setOperator(InfixExpression.Operator.EQUALS);
				}
			} else if (sentinel.getNotNullValue() != null) {
				if (equalityExpr.toString().equals(sentinel.getNotNullValue())) {
					newCheck.setOperator(InfixExpression.Operator.EQUALS);
				} else {
					newCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
				}

			}
			return newCheck;
		}
		return null;

	}
}

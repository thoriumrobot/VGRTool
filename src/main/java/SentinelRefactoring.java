import java.util.Enumeration;
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
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class represents a refactoring in which integer variables whose values
 * represent the nullness of another variables are refactored into explicit null
 * checks
 */
public class SentinelRefactoring extends Refactoring {
	public static final String NAME = "SentinelRefactoring";
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * List of all sentinels found during traversal of the AST
	 */
	private final Hashtable<String, Sentinel> sentinels;

	/**
	 * Used to skip reparsing AST elements that have already been analyzed
	 */
	private boolean skipSentinelDeclaration = false;

	/**
	 * Helper class for storing the AST element of a sentinel reference
	 */
	private class Sentinel {
		public Assignment sentinel_assignment;
		public InfixExpression null_check;
		public Object lastValue;

		public Sentinel(Assignment sentinel_assignment, InfixExpression null_check, Object lastValue) {
			this.sentinel_assignment = sentinel_assignment;
			this.null_check = null_check;
			this.lastValue = lastValue;
		}

		public String toString() {
			return "Sentinel:\n\tSentinel_Assignment: " + this.sentinel_assignment + "\n\tNull_Check: "
					+ this.null_check;
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
		if (sentinels.get(varName.getIdentifier()) != null) {
			sentinels.remove(varName.getIdentifier());
		}
	}

	/*
	 * Checks VariableDeclaration to see if a new variable is declared which shadows
	 * a global variable used as a Sentinel.
	 */
	private void checkShadowing(VariableDeclarationStatement declaration) {
		for (VariableDeclarationFragment fragment : (List<VariableDeclarationFragment>) declaration
				.fragments()) {
			SimpleName varName = fragment.getName();
			if (sentinels.get(varName.getIdentifier()) != null) {
				sentinels.remove(varName.getIdentifier());
			}

		}
	}

	private boolean isValidSentinel(Assignment sentinel_assignment, Expression null_check, Object newValue) {

		LOGGER.debug("Parsing Sentinel: " + sentinel_assignment + ", " + null_check + ", " + newValue);
		// Check if the variable is in sentinels map
		if (!(sentinel_assignment.getLeftHandSide() instanceof SimpleName name)) {
			LOGGER.debug("sentinel_assignment has no simplename");
			return false;
		}
		SimpleName sentinelName = name;
		Sentinel sentinel_value = sentinels.get(sentinelName.getIdentifier());
		if (sentinel_value == null) {
			LOGGER.error("Error when parsing sentinel validity: No sentinel value found for "
					+ sentinelName);
			return false;
		}

		Object lastValue = sentinel_value.lastValue;

		if (lastValue == null) {
			LOGGER.debug("Last value of Sentinel \"" + sentinelName
					+ "\" is unknown. The Sentinel is invalid.");
			return false;
		}

		// Check if the value being assigned to the variable is the same as it's current
		// value
		if (lastValue == newValue) {
			LOGGER.debug("New value of Sentinel \"" + sentinelName
					+ "\" matches old value. The Sentinel is invalid.");
			LOGGER.debug("\tOld Value: " + lastValue);
			LOGGER.debug("\tNew Value: " + newValue);
			return false;
		}
		return true;

	}

	private void updateSentinelValues(ASTNode node) {
		if (node instanceof VariableDeclaration declaration) {
			updateSentinelValues(declaration);
		} else if (node instanceof Assignment assign) {
			updateSentinelValues(assign);
		} else if (node instanceof MethodInvocation || node instanceof SuperMethodInvocation) {
			LOGGER.debug("Clearing all sentinel values due to method invocation...");
			Enumeration<String> keys = sentinels.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				Sentinel sentinel = sentinels.get(key);
				sentinel.lastValue = null;
				sentinels.put(key, sentinel);
			}
		}
	}

	private void updateSentinelValues(String key, Object newValue) {
		Sentinel sentinel = sentinels.get(key);
		if (sentinel == null) {
			sentinels.put(key, new Sentinel(null, null, newValue));
			return;
		}
		sentinel.lastValue = newValue;
		sentinels.put(key, sentinel);

	}

	private void updateSentinelValues(VariableDeclaration declaration) {
		String key = declaration.getName().getIdentifier();
		Object newValue = declaration.getInitializer().resolveConstantExpressionValue();
		updateSentinelValues(key, newValue);
	}

	private void updateSentinelValues(Assignment statement) {
		if (!(statement.getLeftHandSide() instanceof SimpleName varName)) {
			return;
		}

		String key = varName.getIdentifier();
		Object newValue = statement.getRightHandSide().resolveConstantExpressionValue();
		updateSentinelValues(key, newValue);
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
			if (isApplicable(ifStmt)) {
				updateSentinelValues(node);
				return true;
			}
		}
		updateSentinelValues(node);
		if (node instanceof Assignment assign) {
			checkReassignment(assign);
		} else if (node instanceof VariableDeclarationStatement declaration) {
			checkShadowing(declaration);
		}
		return false;

	}

	public boolean isApplicable(IfStatement ifStmt) {
		List<Expression> exprs = Refactoring.getSubExpressions(ifStmt.getExpression());
		for (Expression expression : exprs) {
			if (!(expression instanceof InfixExpression infix)) {
				continue;
			}

			// Checks if node represents a check on a known sentinel value
			if (isApplicable(infix)) {
				return true;
			}

			// Parse through infix statement to see if there are any sentinels
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
		boolean usesSentinel = ((leftOperand instanceof SimpleName lhs
				&& sentinels.get(lhs.getIdentifier()) != null
				&& sentinels.get(lhs.getIdentifier()).sentinel_assignment != null)
				|| (rightOperand instanceof SimpleName rhs
						&& sentinels.get(rhs.getIdentifier()) != null)
						&& sentinels.get(rhs.getIdentifier()).sentinel_assignment != null);
		if (isEqualityCheck && usesSentinel) {
			return true;
		}
		return false;
	}

	public void parseSentinels(InfixExpression null_check, IfStatement ifStmt) {
		if (!isNullCheck(null_check)) {
			return;
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
				&& exprStmt.getExpression() instanceof Assignment sentinel_assignment)) {
			return;
		}

		if (!(sentinel_assignment.getLeftHandSide() instanceof SimpleName var_name)) {
			return;
		}
		if (sentinel_assignment.getOperator() != Assignment.Operator.ASSIGN) {
			return;
		}

		Object sentinel_val = sentinel_assignment.getRightHandSide().resolveConstantExpressionValue();

		if (isValidSentinel(sentinel_assignment, null_check, sentinel_val)) {
			Sentinel new_sentinel = new Sentinel(sentinel_assignment, null_check, sentinel_val);
			LOGGER.debug("Parsed Sentinel: " + new_sentinel);
			sentinels.put(var_name.getIdentifier(), new_sentinel);
			skipSentinelDeclaration = true;
		} else {
			LOGGER.debug("New Sentinel is invalid.");
		}

	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		if (!(node instanceof IfStatement ifStmt)) {
			return;
		}

		List<Expression> exprs = Refactoring.getSubExpressions(ifStmt.getExpression());
		for (Expression expression : exprs) {
			if (!(expression instanceof InfixExpression condition)) {
				continue;
			}

			Expression condLeftOperand = condition.getLeftOperand();
			Expression condRightOperand = condition.getRightOperand();
			InfixExpression.Operator cond_op = condition.getOperator();

			boolean isEqualityCheck = (cond_op == InfixExpression.Operator.NOT_EQUALS
					|| cond_op == InfixExpression.Operator.EQUALS);

			if (!isEqualityCheck) {
				continue;
			}

			SimpleName cond_var;
			Expression cond_val;

			if (condLeftOperand instanceof SimpleName varName) {
				cond_var = varName;
				cond_val = condRightOperand;
			} else if (condRightOperand instanceof SimpleName varName) {
				cond_var = varName;
				cond_val = condLeftOperand;
			} else {
				continue;
			}
			Sentinel sentinel = sentinels.get(cond_var.getIdentifier());
			if (sentinel == null) {
				continue;
			}
			Assignment sentinel_assignment = sentinel.sentinel_assignment;
			if (sentinel_assignment == null) {
				continue;
			}
			Expression sent_val = sentinel_assignment.getRightHandSide();

			InfixExpression null_check = sentinel.null_check;
			InfixExpression.Operator null_check_op = null_check.getOperator();

			AST ast = node.getAST();
			InfixExpression replacement = (InfixExpression) ASTNode.copySubtree(ast, null_check);
			boolean originalValueMatch = sent_val.resolveConstantExpressionValue() == cond_val
					.resolveConstantExpressionValue();
			replacement.setOperator(getRefactoredOperator(null_check_op, cond_op, originalValueMatch));
			rewriter.replace(expression, replacement, null);

		}
	}

	public InfixExpression.Operator reverseOperator(InfixExpression.Operator op) {
		if (op == InfixExpression.Operator.EQUALS) {
			return InfixExpression.Operator.NOT_EQUALS;
		} else if (op == InfixExpression.Operator.NOT_EQUALS) {
			return InfixExpression.Operator.EQUALS;
		}
		return null;

	}

	public InfixExpression.Operator getRefactoredOperator(Operator null_check_op, Operator sentinel_check_op,
			boolean originalValueMatch) {
		Operator refactoredOperator = originalValueMatch ? null_check_op : reverseOperator(null_check_op);
		if (null_check_op != sentinel_check_op && sentinel_check_op == Operator.NOT_EQUALS) {
			return (reverseOperator(refactoredOperator));
		}
		return refactoredOperator;
	}

	public boolean isNullCheck(InfixExpression null_check) {
		Expression leftOperand = null_check.getLeftOperand();
		Expression rightOperand = null_check.getRightOperand();

		boolean leftVarRightNull = (leftOperand instanceof SimpleName && rightOperand instanceof NullLiteral);
		boolean leftNullRightVar = (rightOperand instanceof SimpleName && leftOperand instanceof NullLiteral);
		boolean isEqualityCheck = (null_check.getOperator() == InfixExpression.Operator.EQUALS
				|| null_check.getOperator() == InfixExpression.Operator.NOT_EQUALS);
		return (leftVarRightNull || leftNullRightVar) && isEqualityCheck;
	}

	public Integer ParseInt(String number) {
		try {
			return Integer.valueOf(number);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}

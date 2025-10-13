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
	 * List of all variables which could be sentinels and their associated AST
	 * information
	 */
	private final Hashtable<String, Sentinel> sentinelCandidates;

	/**
	 * Used to skip reparsing AST elements that have already been analyzed
	 */
	private boolean skipSentinelDeclaration = false;

	/**
	 * Helper class for storing the AST element of a sentinel reference and it's
	 * associated AST elements
	 */
	private class Sentinel {
		/**
		 * The original assignment statement setting the sentinel's value
		 */
		public Assignment sentinel_assignment;
		/**
		 * The conditional expression used to decide the value of the sentinel
		 */
		public InfixExpression null_check;
		/**
		 * The last value assigned to the sentinel; Used for validity tracking
		 */
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
		this.sentinelCandidates = new Hashtable<>();
	}

	/*
	 * Detects reassignments of existing sentinels If reassignment is detected,
	 * removes the sentinel from the list of valid sentinels
	 */
	private void checkReassignment(Assignment assignmentNode) {
		Expression lhs = assignmentNode.getLeftHandSide();
		if (!(lhs instanceof SimpleName varName)) {
			return;
		}
		if (sentinelCandidates.get(varName.getIdentifier()) != null) {
			sentinelCandidates.remove(varName.getIdentifier());
		}
	}

	/*
	 * Detects sentinels which are shadowed by new local variables and removes them
	 */
	private void checkShadowing(VariableDeclarationStatement declaration) {
		for (VariableDeclarationFragment fragment : (List<VariableDeclarationFragment>) declaration.fragments()) {
			SimpleName varName = fragment.getName();
			if (sentinelCandidates.get(varName.getIdentifier()) != null) {
				sentinelCandidates.remove(varName.getIdentifier());
			}

		}
	}

	/**
	 * Determines whether a possible sentinel value is valid (i.e. safely
	 * refactorable) by analyzing its associated components
	 * 
	 * @param sentinel_assignment
	 *            The original assignment statement setting the sentinel's value
	 * @param null_check
	 *            The conditional expression used to decide the value of the
	 *            sentinel
	 * @param newValue
	 *            The value assigned to the sentinel when the null_check condition
	 *            is true
	 */
	private boolean isValidSentinel(Assignment sentinel_assignment, Expression null_check, Object newValue) {

		LOGGER.debug("Parsing Sentinel: " + sentinel_assignment + ", " + null_check + ", " + newValue);

		if (!(sentinel_assignment.getLeftHandSide() instanceof SimpleName sentinelName)) {
			LOGGER.debug("Failed to retrieve variable name from sentinel_assignment.");
			return false;
		}

		// Check if the variable is in map of sentinel candidates
		Sentinel sentinel_value = sentinelCandidates.get(sentinelName.getIdentifier());
		if (sentinel_value == null) {
			LOGGER.debug("Sentinel '" + sentinelName + "' is not a sentinel candidate. The Sentinel is invalid.");
			return false;
		}

		// Ensure we are setting the sentinel to a new, distinct value so that we know
		// whether the null_check condition returned true or not.
		Object lastValue = sentinel_value.lastValue;
		if (lastValue == null) {
			LOGGER.debug("Last value of Sentinel \"" + sentinelName + "\" is unknown.");
			return false;
		}
		if (lastValue == newValue) {
			LOGGER.debug("New value of Sentinel \"" + sentinelName + "\" matches old value.");
			LOGGER.debug("\tOld Value: " + lastValue);
			LOGGER.debug("\tNew Value: " + newValue);
			return false;
		}

		LOGGER.debug("Sentinel \"" + sentinelName + "\" is valid.");
		return true;

	}

	private void updateSentinelValues(ASTNode node) {
		if (node instanceof VariableDeclaration declaration) {
			updateSentinelValues(declaration);
		} else if (node instanceof Assignment assign) {
			updateSentinelValues(assign);
		} else if (node instanceof MethodInvocation || node instanceof SuperMethodInvocation) {
			LOGGER.debug("Clearing all sentinel values due to method invocation...");
			Enumeration<String> keys = sentinelCandidates.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				Sentinel sentinel = sentinelCandidates.get(key);
				sentinel.lastValue = null;
				sentinelCandidates.put(key, sentinel);
			}
		}
	}

	private void updateSentinelValues(String key, Object newValue) {
		Sentinel sentinel = sentinelCandidates.get(key);
		if (sentinel == null) {
			sentinelCandidates.put(key, new Sentinel(null, null, newValue));
			return;
		}
		sentinel.lastValue = newValue;
		sentinelCandidates.put(key, sentinel);

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

	/**
	 * Parses IfStatement node to see if it either declares or utilizes a sentinel
	 * 
	 * @param ifStmt
	 *            The node to parse
	 */
	public boolean isApplicable(IfStatement ifStmt) {
		// Parse IfStatement block for declarations of sentinel candidates
		parseSentinels(ifStmt);

		List<Expression> exprs = Refactoring.getSubExpressions(ifStmt.getExpression());
		for (Expression expression : exprs) {
			if (expression instanceof InfixExpression infix) {
				return isApplicable(infix);
			}
		}
		return false;
	}

	/**
	 * Parses InfixExpression node to see if it either declares or utilizes a
	 * sentinel
	 * 
	 * @param ifStmt
	 *            The node to parse
	 */
	public boolean isApplicable(InfixExpression infix) {
		Expression leftOperand = infix.getLeftOperand();
		Expression rightOperand = infix.getRightOperand();
		InfixExpression.Operator operator = infix.getOperator();

		// Check if the condition does a check on an existing sentinel
		boolean isEqualityCheck = isEqualityCheck(operator);
		boolean usesSentinel = usesSentinel(leftOperand) || usesSentinel(rightOperand);
		return (isEqualityCheck && usesSentinel);
	}

	/**
	 * Detects whether an Expression utilizes a sentinel candidate
	 * 
	 * @param expr
	 *            the Expression to parse
	 */
	private boolean isEqualityCheck(InfixExpression.Operator operator) {
		return ((operator == InfixExpression.Operator.NOT_EQUALS || operator == InfixExpression.Operator.EQUALS));
	}

	/**
	 * Detects whether an Expression utilizes a sentinel candidate
	 * 
	 * @param expr
	 *            the Expression to parse
	 */
	private boolean usesSentinel(Expression expr) {
		if (!(expr instanceof SimpleName sentinel_name)) {
			return false;
		}
		Sentinel sentinelCandidate = sentinelCandidates.get(sentinel_name.getIdentifier());
		if (sentinelCandidate == null) {
			return false;
		}
		return sentinelCandidate.sentinel_assignment != null;
	}

	/**
	 * Parses IfStatement node for the creation of sentinels
	 * 
	 * @param ifStmt
	 *            The node to parse
	 */
	public void parseSentinels(IfStatement ifStmt) {
		// Check if IfStatement conditonal utilizes a null check
		InfixExpression null_check = parseNullCheck(Refactoring.getSubExpressions(ifStmt.getExpression()));
		if (null_check == null) {
			return;
		}

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
			sentinelCandidates.put(var_name.getIdentifier(), new_sentinel);
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

			if (!isEqualityCheck(cond_op)) {
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
			Sentinel sentinel = sentinelCandidates.get(cond_var.getIdentifier());
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

	/**
	 * Returns the opposite of the given InfixExpression equality operator
	 */
	private InfixExpression.Operator reverseOperator(InfixExpression.Operator op) {
		if (op == InfixExpression.Operator.EQUALS) {
			return InfixExpression.Operator.NOT_EQUALS;
		} else if (op == InfixExpression.Operator.NOT_EQUALS) {
			return InfixExpression.Operator.EQUALS;
		}
		return null;
	}

	/**
	 * Returns the conditonal operator to use in a refactored null check
	 */
	public InfixExpression.Operator getRefactoredOperator(Operator null_check_op, Operator sentinel_check_op,
			boolean originalValueMatch) {
		Operator refactoredOperator = originalValueMatch ? null_check_op : reverseOperator(null_check_op);
		if (null_check_op != sentinel_check_op && sentinel_check_op == Operator.NOT_EQUALS) {
			return (reverseOperator(refactoredOperator));
		}
		return refactoredOperator;
	}

	/**
	 * Detects and returns a null check in a list of expressions
	 * 
	 * @param exprs
	 *            A list of expressions to parse.
	 */
	public InfixExpression parseNullCheck(List<Expression> exprs) {
		for (Expression expr : exprs) {
			if (expr instanceof InfixExpression null_check_candidate) {
				Expression leftOperand = null_check_candidate.getLeftOperand();
				Expression rightOperand = null_check_candidate.getRightOperand();

				boolean leftVarRightNull = (leftOperand instanceof SimpleName && rightOperand instanceof NullLiteral);
				boolean leftNullRightVar = (rightOperand instanceof SimpleName && leftOperand instanceof NullLiteral);
				boolean validComparison = (leftVarRightNull || leftNullRightVar);
				boolean validOperator = isEqualityCheck(null_check_candidate.getOperator());
				if (validComparison && validOperator) {
					return null_check_candidate;
				}
			}
		}
		return null;
	}
}

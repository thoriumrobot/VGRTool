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
	protected record Sentinel(Assignment sentinel_assignment, InfixExpression null_check) {
		public Expression SentinelName() {
			return sentinel_assignment.getLeftHandSide();
		}

		public String toString() {
			return "Sentinel:\n\tSentinel_Assignment: " + sentinel_assignment() + "\n\tNull_Check: " + null_check();
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
			System.out.println("Checking ifStmt");
			return isApplicable(ifStmt);
		} else if (node instanceof Assignment assign) {
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
				System.out.println("infix applicable");
				return true;
			}

			// Parse through infix statement to see if there are any sentinels
			parseSentinels(infix, ifStmt);
		}
		return false;
	}

	// Todo: Confirm this still works
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
		if (!usesSentinel) {
			System.out.println(infix + " doesn't use a sentinel.");
			System.out.println("Sentinels:\n\t" + sentinels);
		}

		return false;
	}

	public void parseSentinels(InfixExpression null_check, IfStatement ifStmt) {
		System.out.println("Parsing Sentinels...");

		if (!isNullCheck(null_check)) {
			System.out.println("Parsing Sentinel Failed: " + null_check + " is not an null check");
			return;
		}

		// Get then statement
		if (!(ifStmt.getThenStatement() instanceof Block thenStmt)) {
			System.out.println("Parsing Sentinel Failed: Not able to get then stmt");
			return;
		}

		List<Statement> stmts = thenStmt.statements();

		// Checks that there is only one line in the ifStatement
		if (stmts.size() != 1) {
			System.out.println("Parsing Sentinel Failed: thenStmt length > 1");
			return;
		}

		// Checks that the single line is an assignment statement
		if (!(stmts.get(0) instanceof ExpressionStatement exprStmt
				&& exprStmt.getExpression() instanceof Assignment sentinel_assignment)) {
			System.out.println("Parsing Sentinel Failed: Not an Assignment");
			return;
		}

		if (!(sentinel_assignment.getLeftHandSide() instanceof SimpleName var_name)) {
			System.out.println("Parsing Sentinel Failed: LHS not a SimpleName");
			return;
		}
		if (sentinel_assignment.getOperator() != Assignment.Operator.ASSIGN) {
			System.out.println("Parsing Sentinel Failed: OP not an assign");
			return;
		}

		if (ParseInt(sentinel_assignment.getRightHandSide().toString()) == null) {
			System.out.println("Parsing Sentinel Failed: sentinel_assignment is not an int");
			return;
		}

		Sentinel new_sentinel = new Sentinel(sentinel_assignment, null_check);
		sentinels.put(var_name.toString(), new_sentinel);

		skipSentinelDeclaration = true;
		System.out.println("Parsing Sentinel Succeeded! Parsed Sentinel: " + new_sentinel);
	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		System.out.println("Applying refactoring to node...");
		if (!(node instanceof IfStatement ifStmt)) {
			System.out.println("Bruh...");
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
				System.out.println("Not an equality check");
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
			System.out.println(
					"Condition:\n\tCond_Var: " + cond_var + "\n\tCond_Op: " + cond_op + "\n\tCond_val: " + cond_val);

			Sentinel sentinel = sentinels.get(cond_var.toString());
			if (sentinel == null) {
				// TODO: Remove debug log here
				System.out.println("Did not find sentinel value for cond_var: " + cond_var.toString());
				continue;
			}
			Assignment sentinel_assignment = sentinel.sentinel_assignment();
			Expression sent_val = sentinel_assignment.getRightHandSide();

			InfixExpression null_check = sentinel.null_check();
			InfixExpression.Operator null_check_op = null_check.getOperator();

			AST ast = node.getAST();
			InfixExpression replacement = (InfixExpression) ASTNode.copySubtree(ast, null_check);

			if (ParseInt(sent_val.toString()) == ParseInt(cond_val.toString())) {
				if (null_check_op != cond_op) {
					System.out.println(
							"Comparisons Unequal:\n\tnull_check_op: " + null_check_op + "\n\tcond_op: " + cond_op);
					System.out.println("Reversing Operator: " + null_check_op);
					replacement.setOperator(reverseOperator(null_check_op));
					System.out.println("Reversed Operator: " + replacement.getOperator());
				}
			} else {
				System.out.println("Comparison Failed:\n\tsent_val: " + sent_val + "\n\tcond_val: " + cond_val);
			}
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

	public boolean isNullCheck(InfixExpression null_check) {
		Expression leftOperand = null_check.getLeftOperand();
		Expression rightOperand = null_check.getRightOperand();

		boolean leftVarRightNull = (leftOperand instanceof SimpleName && rightOperand instanceof NullLiteral);
		boolean leftNullRightVar = (rightOperand instanceof SimpleName && leftOperand instanceof NullLiteral);
		boolean isEqualityCheck = (null_check.getOperator() == InfixExpression.Operator.EQUALS
				|| null_check.getOperator() == InfixExpression.Operator.NOT_EQUALS);
		System.out.println((leftVarRightNull || leftNullRightVar));
		System.out.println(isEqualityCheck);
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

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
	private final Hashtable<String, Sentinel> sentinels;
	private final Hashtable<String, Sentinel_Value> sentinel_values;

	/**
	 * Used to skip reparsing AST elements that have already been analyzed
	 */
	private boolean skipSentinelDeclaration = false;

	/**
	 * Helper class for storing the AST element of a sentinel reference
	 */
	protected record Sentinel(Assignment sentinel_assignment, InfixExpression null_check) {
		public Expression SentinelName() {
			return sentinel_assignment.getLeftHandSide();
		}

		public String toString() {
			return "Sentinel:\n\tSentinel_Assignment: " + sentinel_assignment() + "\n\tNull_Check: " + null_check();
		}
	}

	/**
	 * Helper class for storing the value of a sentinel reference and whether it is
	 * known or not
	 * 
	 * @param known
	 *            Whether the sentinel variable has a known value (can be unset)
	 * @param value
	 *            The value of the sentinel variable. Null signifies the variable is
	 *            declared but unset
	 */
	protected record Sentinel_Value(boolean known, Expression value) {
	};

	public SentinelRefactoring() {
		super();
		this.sentinels = new Hashtable<>();
		this.sentinel_values = new Hashtable<>();
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

	private boolean isValidSentinel(Sentinel sentinel, Expression new_value) {
		System.out.println("Parsing value of Sentinel: " + sentinel.SentinelName());

		// The variable is not in sentinel_values
		// TODO: Add error warning as this should not happen
		Sentinel_Value sentinel_value = sentinel_values.get(sentinel.SentinelName().toString());
		if (sentinel_value == null) {
			System.out.println("\t ERROR: No sentinel value found for variable " + sentinel.SentinelName());
			System.out.println("\t sentinels_values: \n\t\t" + sentinel_values);
			return false;
		}
		System.out.println("\t sentinels_values: \n\t\t" + sentinel_values);

		Expression valueExpr = sentinel_value.value();

		// The variable was previously unset
		if (valueExpr == null) {
			System.out.println("\t sentinel_value is currently unset");
			return true;
		}

		// TODO: Confirm this works with non-ints
		// The variable previously had the same value as the potential new value
		if (valueExpr.resolveConstantExpressionValue() == new_value.resolveConstantExpressionValue()) {
			System.out.println("\t new_sentinel matches old value");
			return false;
		}
		return true;

	}

	private void updateSentinelValues(ASTNode node) {
		// TODO: Confirm works with the classes VariableDeclaration,
		// SingleVariableDeclaration
		if (node instanceof VariableDeclaration declaration) {
			updateSentinelValues(declaration);
		} else if (node instanceof Assignment assign) {
			updateSentinelValues(assign);
		} else if (node instanceof MethodInvocation || node instanceof SuperMethodInvocation) {
			System.out.println("Clearing all sentinel values due to method invocation...");
			Enumeration<String> keys = sentinel_values.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				sentinel_values.put(key, new Sentinel_Value(false, null));
			}
		}
	}

	private void updateSentinelValues(VariableDeclaration declaration) {
		SimpleName varName = declaration.getName();
		if (sentinel_values.get(varName.toString()) != null) {
			System.out.println("Sentinel \"" + varName + "\" redeclared...somehow. Shadowing?");
			System.out.println("\t sentinels_values: \n\t\t" + sentinel_values);
			// WARN: Unknown if this is an error
			// sentinel_values.remove(varName.toString());
		} else {
			System.out.println(
					"Possible Sentinel Declared: \n\tName: " + varName + "\n\tValue: " + declaration.getInitializer());
			sentinel_values.put(varName.toString(), new Sentinel_Value(false, declaration.getInitializer()));
		}
	}

	private void updateSentinelValues(Assignment statement) {
		if (!(statement.getLeftHandSide() instanceof SimpleName varName)) {
			System.out.println("Declaration of possible declaration \"" + statement.getLeftHandSide()
					+ "\" failed due to Assignment node not having a SimpleName on the LHS");
			return;
		}

		if (sentinel_values.get(varName.toString()) != null) {
			System.out.println("Possible Sentinel Value Updated: \n\tName: " + varName + "\n\tValue: "
					+ statement.getRightHandSide());
			sentinel_values.put(varName.toString(), new Sentinel_Value(false, statement.getRightHandSide()));
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

		// FIX: Should work for more than integers
		Expression sentinel_val = sentinel_assignment.getRightHandSide();
		if (ParseInt(sentinel_val.toString()) == null) {
			System.out.println("Parsing Sentinel Failed: sentinel_assignment is not an int");
			return;
		}

		Sentinel new_sentinel = new Sentinel(sentinel_assignment, null_check);
		if (isValidSentinel(new_sentinel, sentinel_val)) {
			System.out.println("Parsing Sentinel Succeeded! Parsed Sentinel: " + new_sentinel);
			sentinels.put(var_name.toString(), new_sentinel);
			skipSentinelDeclaration = true;
		} else {
			System.out.println("Parsing Sentinel Failed. New Sentinel value not valid.");
		}

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
			// TODO: Confirm this works with non-ints
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
		System.out.println("null_check_op: " + null_check_op);
		System.out.println("sentinel_check_op: " + sentinel_check_op);
		if (null_check_op != sentinel_check_op && sentinel_check_op == Operator.NOT_EQUALS) {
			System.out.println("Reversing \"" + null_check_op + "\"...");
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

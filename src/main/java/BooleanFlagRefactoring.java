import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

// (Assume Refactoring is an abstract base class provided in the same framework)
public class BooleanFlagRefactoring extends Refactoring {

	/**
	 * List of variable names idnetified as boolean flags
	 */
	private final Dictionary<String, Expression> booleanFlags;

	/** Default constructor (for RefactoringEngine integration) */
	public BooleanFlagRefactoring() {
		super();
		this.booleanFlags = new Hashtable<>();
	}

	private boolean isFlag(SimpleName varName) {
		String name = varName.toString();
		return booleanFlags.get(name) != null;
	}

	private Expression getMatchingExpr(SimpleName varName) {
		String name = varName.toString();
		booleanFlags.get(name);
		return booleanFlags.get(name);
	}

	private boolean isApplicable(SimpleName varName, Expression expr, Expression originalExpression) {
		if (expr instanceof ParenthesizedExpression pExpr) {
			isApplicable(varName, pExpr.getExpression(), originalExpression);
		}
		if (expr instanceof InfixExpression infix) {
			System.out.println(0);
			if (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
					|| infix.getOperator() == InfixExpression.Operator.EQUALS) {
				System.out.println(1);

				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();
				if ((leftOperand instanceof SimpleName && rightOperand instanceof NullLiteral)
						|| (rightOperand instanceof SimpleName && leftOperand instanceof NullLiteral)) {
					System.out.println("[DEBUG] Found boolean flag in VariableDeclarationStatement: Variable " + varName
							+ " with initializer expression " + expr);
					System.out.println("[DEBUG] Adding key " + varName + " to dict with value " + originalExpression);
					booleanFlags.put(varName.toString(), originalExpression);
					return true;
				}
			}
			if (infix.getOperator().toString().equals("&&")) {
				System.out.println(2);
				boolean leftApplicable = isApplicable(varName, infix.getLeftOperand(), originalExpression);
				boolean rightApplicable = isApplicable(varName, infix.getRightOperand(), originalExpression);
				return (leftApplicable && rightApplicable);
			} else if (infix.getOperator().toString().equals("||")) {
				System.out.println(3);
				boolean leftApplicable = isApplicable(varName, infix.getLeftOperand(), originalExpression);
				boolean rightApplicable = isApplicable(varName, infix.getRightOperand(), originalExpression);
				return (leftApplicable || rightApplicable);
			}
			System.out.println(4 + " " + infix.getOperator());
		}
		return false;
	}

	@Override
	public boolean isApplicable(ASTNode node) {
		System.out.println("[DEBUG] Checking if node is applicable: " + node.getClass().getSimpleName());

		if (node instanceof IfStatement ifStmt) {
			Expression condition = ifStmt.getExpression();
			if (condition instanceof InfixExpression infix
					&& (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
							|| infix.getOperator() == InfixExpression.Operator.EQUALS)) {
				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();

				if ((leftOperand instanceof SimpleName lhs && isFlag((SimpleName) lhs))
						|| (rightOperand instanceof SimpleName rhs && isFlag((SimpleName) rhs))) {
					System.out.println("[DEBUG] Found booleanflag in if-statement: " + condition);
					return true;
				}
			}
			if (condition instanceof SimpleName sn && isFlag(sn)) {
				System.out.println("[DEBUG] Found booleanflag in if-statement: " + condition);
				return true;
			}
			return false;
		}

		if (node instanceof VariableDeclarationStatement stmt) {
			System.out.println(-1);

			Type stmtType = stmt.getType();
			boolean isBooleanDeclaration = (stmtType.isPrimitiveType()
					&& ((PrimitiveType) stmtType).getPrimitiveTypeCode() == PrimitiveType.BOOLEAN);
			if (!isBooleanDeclaration)
				return false;

			boolean flagFound = false;
			for (int i = 0; i < stmt.fragments().size(); ++i) {

				VariableDeclarationFragment frag = (VariableDeclarationFragment) stmt.fragments().get(i);
				System.out.println("Fragment " + i + ": " + frag);

				SimpleName varName = frag.getName();
				Expression varInitializer = frag.getInitializer();
				flagFound = isApplicable(varName, varInitializer, varInitializer);

			}
			return flagFound;
		}
		return false;
	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		if (node instanceof IfStatement ifStmt) {
			Expression condition = ifStmt.getExpression();
			if (condition instanceof InfixExpression infix
					&& (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
							|| infix.getOperator() == InfixExpression.Operator.EQUALS)) {
				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();

				if ((leftOperand instanceof SimpleName var)) {
					Expression newExpr = getMatchingExpr(var);
					System.out.println("[DEBUG] Replacing expression " + leftOperand + " with expression " + newExpr);
					rewriter.replace(leftOperand, newExpr, null);
				} else if (rightOperand instanceof SimpleName var) {
					Expression newExpr = getMatchingExpr(var);
					System.out.println("[DEBUG] Replacing expression " + rightOperand + " with expression " + newExpr);
					rewriter.replace(rightOperand, newExpr, null);
				}
			}
			if (condition instanceof SimpleName sn) {
				Expression newExpr = getMatchingExpr(sn);
				System.out.println("[DEBUG] Replacing SimpleName " + sn + " with expression " + newExpr);
				rewriter.replace(sn, newExpr, null);

			}
		}

	}

}
